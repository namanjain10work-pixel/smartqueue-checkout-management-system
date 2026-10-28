# SmartQueue

**[🚀 Live Demo](https://smartqueue-web.onrender.com/index.html)**

Free hosting may take a couple of minutes to wake up after inactivity.

SmartQueue is a retail checkout queue-management prototype built with Java 21,
Spring Boot, and an optional Python ML prediction service. It assigns customers
to the open counter with the least predicted remaining workload and applies a
bounded assistance-priority policy when calling the next customer.

The main dashboard and API run on port **8080**. The ML service runs on **8001**.
The earlier Python application (`app.main`, port 8000) remains legacy code;
it is not the entry point for this Java-based version.

## Features

- Customer registration, digital tokens, estimated waiting times, and cancellation
- Open-counter allocation by predicted remaining workload, with counter-ID tie breaking
- Queue lifecycle: WAITING -> SERVING -> COMPLETED; WAITING -> CANCELLED
- Assistance customers may advance at most two positions; the oldest regular
  customer is protected after waiting ten minutes
- Counter opening/closing controls, queue history, and dashboard statistics
- Optional Linear Regression predictions over HTTP/1.1 with Java rule fallback
- File-based H2 development storage and a separate PostgreSQL profile
- Automated predictor, allocation, fairness, lifecycle, and application-context tests

## Architecture

1. The browser calls the Spring Boot backend.
2. Spring obtains a duration from the Python ML API when ML is enabled.
3. On unavailable/invalid ML responses, Spring uses its Java rule predictor.
4. CounterAllocator sums waiting durations and remaining serving durations.
5. The least-loaded open counter receives the customer; JPA persists the entry.

Python also has its own rule fallback if model loading or inference fails.
A healthy Python process does not by itself prove that a trained model is loaded.

## Requirements

- Java 21, with JAVA_HOME configured
- Python 3.10+ compatible with the requirements in requirements.txt
- Windows PowerShell for the supplied convenience scripts
- PostgreSQL for the optional postgres profile; PostgreSQL 18 was used locally
- Internet access for initial Maven and Python dependency downloads

Maven Wrapper is included under backend; a global Maven installation is unnecessary.

## Cloud demo deployment

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the Render and Neon setup.
`render.yaml` describes the two free services; credentials are supplied separately.

## Initial Python and model setup

Run from the SmartQueue project root:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

If the synthetic dataset has not been created:

```powershell
.\.venv\Scripts\python.exe scripts\generate_synthetic_data.py
```

Then validate and train:

```powershell
.\.venv\Scripts\python.exe scripts\analyze_data.py
.\.venv\Scripts\python.exe scripts\train_model.py
```

Training writes `ml/checkout_model.joblib` and regenerates `docs/MODEL_RESULTS.md`.
Model files are excluded from Git, so a fresh clone must train before starting ML.
Restart the Python service after training because model loading is cached.

## Start with H2 (no Python required)

From the project root:

```powershell
.\scripts\start-backend.ps1
```

Open <http://localhost:8080/>. Keep the terminal open while using the app.
The default database is `backend/data/smartqueue`.

If your machine blocks local PowerShell scripts, use this command for a single
script invocation, subject to your organization's policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

## Start with PostgreSQL and ML

Create an empty `smartqueue` database using psql or pgAdmin first. For example,
with the PostgreSQL 18 Windows installation:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -h localhost -d postgres -c 'CREATE DATABASE smartqueue;'
```

Start the ML service in terminal 1, from the project root:

```powershell
.\scripts\start-ml.ps1
```

Start Spring Boot in terminal 2, also from the project root:

```powershell
.\scripts\start-backend.ps1 -Database Postgres -EnableMl
```

Enter the PostgreSQL password when prompted; typing is hidden. Existing process
variables SMARTQUEUE_DB_URL, SMARTQUEUE_DB_USERNAME, and SMARTQUEUE_DB_PASSWORD
can override the PostgreSQL connection settings. Do not commit credentials.

Wait for `Started BackendApplication` and `Tomcat started on port 8080`.
Maven's `BUILD SUCCESS` alone is not evidence that the web server is running.
Do not start a second backend from IntelliJ while this process is active.

Check from terminal 3:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8001/health'
Invoke-RestMethod 'http://127.0.0.1:8001/api/ml/status'
Invoke-RestMethod 'http://localhost:8080/api/dashboard/stats'
```

`trained_model_loaded` should be true for an ML demonstration. Use the status
endpoint together with an actual prediction request to verify model operation.
Python API documentation: <http://127.0.0.1:8001/docs>.

H2 and PostgreSQL store separate records; switching profiles does not migrate data.
Press Ctrl+C in each service terminal to stop it. The scripts restore the process
configuration they changed when they exit normally.

## Demonstrate an ML-backed queue assignment

In the testing terminal:

```powershell
$body = @{
    customerName = 'Demo Customer'
    itemCount = 15
    paymentMethod = 'UPI'
    priorityType = 'REGULAR'
} | ConvertTo-Json

$entry = Invoke-RestMethod -Uri 'http://localhost:8080/api/queue/join' -Method Post -ContentType 'application/json' -Body $body
$entry | Format-List
```

With the current trained model loaded, the integrated response should identify
`ML_LINEAR_REGRESSION`. Then use the dashboard to start and complete checkout.
To cancel this demo while it is still WAITING:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/queue/$($entry.id)/cancel" -Method Post
```

Test fallback separately by stopping only Python and joining another customer.
Inspect `predictionSource` for `RULE_BASELINE_V1`, then restart Python.

## Tests and validation

```powershell
.\scripts\test-backend.ps1
```

The verified local suite contains 19 tests: 15 queue-rule tests, 3 predictor tests,
and 1 application-context test. Predictor tests run a temporary local HTTP server;
the ML failure test intentionally produces a 503 fallback warning. Queue-rule
tests use mock repositories. The context test uses the test-resource H2 database.
These do not constitute automated PostgreSQL integration or browser tests.

Manual checks completed locally include a real Spring-to-Python model prediction
and PostgreSQL customer persistence across a backend restart.

## Allocation and fairness

For each open counter, waiting customers contribute their full predicted duration.
Serving customers contribute `max(0, predicted duration - elapsed duration)`.
Counters are ordered by total workload, then ID.

For A active entries across C open counters, workload summation is O(A), heap
construction is O(C), and peeking at the minimum is O(1). The current implementation
also performs one active-entry query per open counter, plus the open-counter query.
It rebuilds the heap for each assignment. These costs exclude database overhead.

The fairness selector expects entries sorted by assignment time. It can advance
an assistance customer from index 0, 1, or 2, except when the first customer is
regular and has waited at least ten minutes. Selection scans the supplied list,
so its current worst-case runtime is O(N).

## ML evaluation

All evaluation data is **synthetic simulation data**, not store observations.
After removing four exact duplicates from 2,500 rows, the split contained 1,996
training/validation rows and 500 held-out test rows. Five-fold cross-validation
was performed on the training partition.

| Candidate | CV MAE (seconds) | Test MAE (seconds) | Test RMSE (seconds) | Test R2 |
|---|---:|---:|---:|---:|
| Linear Regression | 14.95 | 13.93 | 17.46 | 0.873 |
| Rule Baseline V1 | 14.93 | 14.01 | 17.72 | 0.869 |
| Random Forest | 15.87 | 15.18 | 19.01 | 0.850 |
| Decision Tree | 16.85 | 16.71 | 20.92 | 0.818 |

Linear Regression was selected by the lowest CV MAE **among the three ML
candidates**, not among every compared approach. The fixed rule has a slightly
lower CV MAE. Linear Regression's test MAE is only 0.08 seconds lower than the
rule's; this does not establish a meaningful or statistically significant gain.

Reported test scores describe models fitted before final refitting. The selected
model is subsequently refitted on all validated rows for local demonstration;
those test records are therefore no longer unseen by the final saved artifact.
A fresh external dataset is needed to evaluate that artifact independently.

## Current limitations

- Java accepts 1-300 items, while the ML API accepts 1-120. Requests with 121-300
  items currently receive a Python validation error and use the Java rule fallback.
  This limit difference is documented; no ML accuracy is claimed outside that range.
- ML accuracy and reductions in queue waiting time have not been established in
  real stores. Prediction-error metrics do not prove a queue-wait improvement.
- The PostgreSQL profile uses Hibernate ddl-auto=update for this prototype;
  versioned migrations, authentication, concurrent-assignment protection, and
  deployment hardening require additional work before public production use.
- Existing planning documents may describe the earlier Python-only prototype.

## Project layout

- `backend/`: Spring Boot application, static dashboard, Java tests, Maven Wrapper
- `app/ml_api.py`: Python ML-only service used by Spring
- `app/services/predictor.py`: model loading, inference, and Python fallback
- `scripts/`: data generation, analysis, training, and PowerShell launch helpers
- `ml/`: locally generated model artifact
- `data/`: synthetic data and observation template
- `docs/`: analysis, evaluation results, and planning documents
- `app/main.py`, `tests/`: earlier Python MVP and its tests

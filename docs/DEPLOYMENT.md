# SmartQueue public demo deployment

The cloud demo uses two free Render web services and a separate Neon Postgres
project in Singapore. Local development remains unchanged.

## Services

- `smartqueue-web`: Docker-built Java 21 application; context `backend`,
  Dockerfile `backend/Dockerfile`. The build runs `./mvnw -B verify`.
- `smartqueue-ml`: Python 3.12.12. Build command:
  `pip install -r requirements-render.txt && python scripts/train_model.py`.
  Start command:
  `python -m uvicorn app.ml_api:app --host 0.0.0.0 --port $PORT`.

Both services use the free compute plan. The model is generated at build time,
not committed as a binary. The existing tracked synthetic dataset is used.
The dashboard uses relative API paths and remains served by Spring Boot.

## Backend runtime variables

- `SPRING_PROFILES_ACTIVE=postgres,demo`
- `SMARTQUEUE_ML_ENABLED=true`
- `SMARTQUEUE_ML_BASE_URL`: the actual HTTPS URL assigned to the ML service
- `SMARTQUEUE_DB_URL`: JDBC Postgres URL with `sslmode=require`; credentials
  are supplied separately. Prefer the Neon pooled hostname for app traffic.
- `SMARTQUEUE_DB_USERNAME` and `SMARTQUEUE_DB_PASSWORD`: deployment secrets
- `TZ=Asia/Kolkata`
- `PORT`: supplied by Render; Spring defaults to 8080 when it is absent

The demo profile reduces the database pool to three connections and permits
zero idle connections. ML requests have a three-second connection timeout
and a five-second response timeout so an unavailable or sleeping ML process
can fall back to Java rules. No paid service is required by render.yaml.

Free Render services sleep when idle. Open the ML `/api/ml/status` endpoint
before an important demo, wait for `trained_model_loaded: true`, then open the
dashboard. The first prediction after a cold start may use the rule fallback.
This is a demonstration using synthetic details, not a real customer system.
Free-tier usage limits apply; two services share the workspace's monthly hours.
Do not use external keep-alive services to defeat the idle behavior.

## Provisioning

`render.yaml` is an optional reproducible Blueprint. The initial services can
also be created through the connected Render tools with the same settings.
If services were created directly, do not apply the Blueprint blindly or create
duplicate services. Inspect existing service names before choosing management.
The initial creation path and service IDs should be recorded after deployment.

For Blueprint creation, provide the three database variables through Render's
secret input fields. The ML URL is referenced from the other service's
`RENDER_EXTERNAL_URL`; it is not an internal private-network hostname, because
free web services do not accept private-network traffic.

Never commit database passwords or connection URLs containing credentials.
The cloud database is separate from the local Windows PostgreSQL database.
No local records are uploaded by this deployment.

## Verification

1. Confirm both Render deployments are live at the intended Git commit.
2. Check ML `/health` and `/api/ml/status`.
3. Check backend `/api/dashboard/stats` and `/`.
4. Submit a synthetic customer with 15 items, UPI, REGULAR.
5. Verify `predictionSource=ML_LINEAR_REGRESSION`.
6. Start and complete the customer; verify history and dashboard counters.
7. Keep only synthetic demo records.

Changes pushed to GitHub by the deployment workflow must also be pulled into
existing local clones before more local edits are committed.

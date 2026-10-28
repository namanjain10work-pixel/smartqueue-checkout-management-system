# Model Comparison

These results use **synthetic simulation data**, not observations from a
real store. They validate the pipeline only. Real-world performance claims
require permission-based checkout observations.

## Dataset preparation

- Original records: **2500**
- Exact duplicates removed: **4**
- Training and validation records: **1996**
- Independent test records: **500**
- Cross-validation folds: **5**

## Results

| Model | CV MAE | CV standard deviation | Test MAE | Test RMSE | Test R² |
|---|---:|---:|---:|---:|---:|
| Linear Regression | 14.95 | 0.11 | 13.93 | 17.46 | 0.873 |
| Rule Baseline V1 | 14.93 | 0.25 | 14.01 | 17.72 | 0.869 |
| Random Forest | 15.87 | 0.35 | 15.18 | 19.01 | 0.850 |
| Decision Tree | 16.85 | 0.70 | 16.71 | 20.92 | 0.818 |

## Selection

Selected model: **Linear Regression**

Among the three ML candidates, the model was selected using the lowest five-fold cross-validation MAE:
**14.95 seconds**.

Its independent test MAE was **13.93 seconds**.

The existing Spring Boot rule baseline achieved a test MAE of
**14.01 seconds**.

The selected model was retrained using all validated, deduplicated records
and saved for prediction-service integration.

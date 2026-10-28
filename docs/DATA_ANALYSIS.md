# SmartQueue Dataset Analysis

This report was generated automatically by `scripts/analyze_data.py`.

> This dataset is synthetically simulated for development and pipeline
> validation. It must not be presented as real-store evidence.

## Dataset overview

- Records: **2500**
- Columns: **6**
- Data source: **synthetic_simulation**
- Target variable: **service_seconds**

## Validation results

| Check | Problem rows | Severity |
| --- | --- | --- |
| Missing required values | 0 | ERROR |
| Item count outside 1-120 | 0 | ERROR |
| Hour outside 9-21 | 0 | ERROR |
| Day outside 0-6 | 0 | ERROR |
| Service time outside 35-900 seconds | 0 | ERROR |
| Unknown payment method | 0 | ERROR |
| Exact duplicate records | 4 | WARNING |

## Service-time statistics

| Metric | Value |
| --- | --- |
| Records | 2500.00 |
| Mean | 136.67 |
| Median | 130.00 |
| Standard deviation | 48.26 |
| Minimum | 35.00 |
| 25th percentile | 104.00 |
| 75th percentile | 165.00 |
| Maximum | 331.00 |

## Payment-method comparison

| payment_method | Records | Mean | Median | Standard_deviation | Minimum | Maximum |
| --- | --- | --- | --- | --- | --- | --- |
| card | 623 | 139.46 | 133.00 | 48.06 | 35 | 318 |
| cash | 473 | 149.55 | 140.00 | 50.17 | 42 | 331 |
| upi | 1404 | 131.10 | 125.00 | 46.78 | 35 | 326 |

## Peak and non-peak comparison

| period | Records | Mean | Median | Standard_deviation | Minimum | Maximum |
| --- | --- | --- | --- | --- | --- | --- |
| Non-peak | 1366 | 130.90 | 124.00 | 47.69 | 35 | 331 |
| Peak | 1134 | 143.62 | 136.00 | 48.06 | 35 | 326 |

## Weekday and weekend comparison

| week_part | Records | Mean | Median | Standard_deviation | Minimum | Maximum |
| --- | --- | --- | --- | --- | --- | --- |
| Weekday | 1797 | 134.85 | 128.00 | 49.01 | 35 | 326 |
| Weekend | 703 | 141.34 | 136.00 | 46.02 | 36 | 331 |

## Hourly service-time summary

| hour_of_day | Records | Mean | Median | Standard_deviation | Minimum | Maximum |
| --- | --- | --- | --- | --- | --- | --- |
| 9.00 | 205.00 | 131.51 | 127.00 | 48.91 | 35.00 | 326.00 |
| 10.00 | 184.00 | 135.89 | 123.00 | 51.52 | 42.00 | 317.00 |
| 11.00 | 202.00 | 126.92 | 118.50 | 45.82 | 35.00 | 253.00 |
| 12.00 | 197.00 | 143.12 | 134.00 | 50.80 | 40.00 | 302.00 |
| 13.00 | 192.00 | 147.85 | 140.00 | 49.74 | 35.00 | 303.00 |
| 14.00 | 194.00 | 127.78 | 124.00 | 44.91 | 36.00 | 331.00 |
| 15.00 | 192.00 | 129.45 | 123.00 | 43.08 | 43.00 | 266.00 |
| 16.00 | 194.00 | 133.43 | 127.00 | 48.99 | 42.00 | 291.00 |
| 17.00 | 207.00 | 140.80 | 136.00 | 46.15 | 59.00 | 315.00 |
| 18.00 | 185.00 | 148.25 | 141.00 | 49.13 | 47.00 | 295.00 |
| 19.00 | 189.00 | 138.47 | 135.00 | 40.99 | 50.00 | 281.00 |
| 20.00 | 164.00 | 143.55 | 134.00 | 51.10 | 35.00 | 326.00 |
| 21.00 | 195.00 | 131.70 | 126.00 | 50.25 | 35.00 | 285.00 |

## Day-of-week summary

Day numbering uses `0 = Monday` through `6 = Sunday`.

| day_of_week | Records | Mean | Median | Standard_deviation | Minimum | Maximum |
| --- | --- | --- | --- | --- | --- | --- |
| 0.00 | 371.00 | 135.99 | 127.00 | 48.23 | 42.00 | 291.00 |
| 1.00 | 355.00 | 136.33 | 130.00 | 49.13 | 35.00 | 318.00 |
| 2.00 | 341.00 | 138.34 | 133.00 | 49.85 | 35.00 | 326.00 |
| 3.00 | 343.00 | 131.08 | 128.00 | 47.98 | 42.00 | 298.00 |
| 4.00 | 387.00 | 132.65 | 125.00 | 49.71 | 35.00 | 326.00 |
| 5.00 | 364.00 | 142.13 | 135.50 | 45.40 | 54.00 | 331.00 |
| 6.00 | 339.00 | 140.49 | 136.00 | 46.72 | 36.00 | 295.00 |

## Main statistical findings

- Correlation between item count and service time:
  **0.905**
- Peak-hour mean increase:
  **12.72 seconds**
- Weekend mean increase:
  **6.49 seconds**
- Payment methods have different average service times because the
  simulation assigns different payment overheads.
- These findings describe the simulation design and do not prove
  real-world checkout behaviour.

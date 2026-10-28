"""Validate and statistically analyse the SmartQueue training dataset."""

from pathlib import Path

import pandas as pd


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DATA_PATH = PROJECT_ROOT / "data" / "synthetic_checkout_history.csv"
REPORT_PATH = PROJECT_ROOT / "docs" / "DATA_ANALYSIS.md"

REQUIRED_COLUMNS = [
    "item_count",
    "payment_method",
    "hour_of_day",
    "day_of_week",
    "service_seconds",
    "data_source",
]

NUMERIC_COLUMNS = [
    "item_count",
    "hour_of_day",
    "day_of_week",
    "service_seconds",
]

VALID_PAYMENT_METHODS = {"upi", "card", "cash"}
PEAK_HOURS = {12, 13, 17, 18, 19, 20}


def format_value(value) -> str:
    if pd.isna(value):
        return "-"

    if isinstance(value, float):
        return f"{value:.2f}"

    return str(value)


def markdown_table(frame: pd.DataFrame) -> str:
    headers = [str(column) for column in frame.columns]

    header_row = "| " + " | ".join(headers) + " |"
    separator = "| " + " | ".join("---" for _ in headers) + " |"

    data_rows = []

    for _, row in frame.iterrows():
        values = [
            format_value(row[column])
            for column in frame.columns
        ]

        data_rows.append(
            "| " + " | ".join(values) + " |"
        )

    return "\n".join(
        [header_row, separator, *data_rows]
    )


def load_data() -> pd.DataFrame:
    if not DATA_PATH.exists():
        raise SystemExit(
            "Dataset not found. Run "
            "scripts/generate_synthetic_data.py first."
        )

    data = pd.read_csv(DATA_PATH)

    missing_columns = [
        column
        for column in REQUIRED_COLUMNS
        if column not in data.columns
    ]

    if missing_columns:
        raise SystemExit(
            "Dataset is missing columns: "
            + ", ".join(missing_columns)
        )

    for column in NUMERIC_COLUMNS:
        data[column] = pd.to_numeric(
            data[column],
            errors="coerce",
        )

    data["payment_method"] = (
        data["payment_method"]
        .astype("string")
        .str.strip()
        .str.lower()
    )

    data["data_source"] = (
        data["data_source"]
        .astype("string")
        .str.strip()
    )

    return data


def build_validation_summary(
    data: pd.DataFrame,
) -> pd.DataFrame:
    missing_values = int(
        data[REQUIRED_COLUMNS].isna().sum().sum()
    )

    checks = [
        {
            "Check": "Missing required values",
            "Problem rows": missing_values,
            "Severity": "ERROR",
        },
        {
            "Check": "Item count outside 1-120",
            "Problem rows": int(
                (~data["item_count"].between(1, 120)).sum()
            ),
            "Severity": "ERROR",
        },
        {
            "Check": "Hour outside 9-21",
            "Problem rows": int(
                (~data["hour_of_day"].between(9, 21)).sum()
            ),
            "Severity": "ERROR",
        },
        {
            "Check": "Day outside 0-6",
            "Problem rows": int(
                (~data["day_of_week"].between(0, 6)).sum()
            ),
            "Severity": "ERROR",
        },
        {
            "Check": "Service time outside 35-900 seconds",
            "Problem rows": int(
                (~data["service_seconds"].between(35, 900)).sum()
            ),
            "Severity": "ERROR",
        },
        {
            "Check": "Unknown payment method",
            "Problem rows": int(
                (
                    ~data["payment_method"].isin(
                        VALID_PAYMENT_METHODS
                    )
                ).sum()
            ),
            "Severity": "ERROR",
        },
        {
            "Check": "Exact duplicate records",
            "Problem rows": int(data.duplicated().sum()),
            "Severity": "WARNING",
        },
    ]

    return pd.DataFrame(checks)


def grouped_summary(
    data: pd.DataFrame,
    group_column: str,
) -> pd.DataFrame:
    return (
        data.groupby(group_column)["service_seconds"]
        .agg(
            Records="count",
            Mean="mean",
            Median="median",
            Standard_deviation="std",
            Minimum="min",
            Maximum="max",
        )
        .reset_index()
        .round(2)
    )


def main() -> None:
    data = load_data()

    validation = build_validation_summary(data)

    error_count = int(
        validation.loc[
            validation["Severity"] == "ERROR",
            "Problem rows",
        ].sum()
    )

    if error_count > 0:
        print(markdown_table(validation))

        raise SystemExit(
            "Dataset validation failed. "
            "Correct the ERROR rows before training."
        )

    data["period"] = data["hour_of_day"].apply(
        lambda hour: "Peak" if hour in PEAK_HOURS
        else "Non-peak"
    )

    data["week_part"] = data["day_of_week"].apply(
        lambda day: "Weekend" if day >= 5
        else "Weekday"
    )

    service_summary = pd.DataFrame(
        {
            "Metric": [
                "Records",
                "Mean",
                "Median",
                "Standard deviation",
                "Minimum",
                "25th percentile",
                "75th percentile",
                "Maximum",
            ],
            "Value": [
                len(data),
                data["service_seconds"].mean(),
                data["service_seconds"].median(),
                data["service_seconds"].std(),
                data["service_seconds"].min(),
                data["service_seconds"].quantile(0.25),
                data["service_seconds"].quantile(0.75),
                data["service_seconds"].max(),
            ],
        }
    ).round(2)

    payment_summary = grouped_summary(
        data,
        "payment_method",
    )

    hourly_summary = grouped_summary(
        data,
        "hour_of_day",
    )

    day_summary = grouped_summary(
        data,
        "day_of_week",
    )

    period_summary = grouped_summary(
        data,
        "period",
    )

    week_summary = grouped_summary(
        data,
        "week_part",
    )

    correlation = data[
        ["item_count", "service_seconds"]
    ].corr().iloc[0, 1]

    peak_mean = data.loc[
        data["period"] == "Peak",
        "service_seconds",
    ].mean()

    non_peak_mean = data.loc[
        data["period"] == "Non-peak",
        "service_seconds",
    ].mean()

    weekend_mean = data.loc[
        data["week_part"] == "Weekend",
        "service_seconds",
    ].mean()

    weekday_mean = data.loc[
        data["week_part"] == "Weekday",
        "service_seconds",
    ].mean()

    sources = ", ".join(
        sorted(data["data_source"].dropna().unique())
    )

    report = f"""# SmartQueue Dataset Analysis

This report was generated automatically by `scripts/analyze_data.py`.

> This dataset is synthetically simulated for development and pipeline
> validation. It must not be presented as real-store evidence.

## Dataset overview

- Records: **{len(data)}**
- Columns: **{len(REQUIRED_COLUMNS)}**
- Data source: **{sources}**
- Target variable: **service_seconds**

## Validation results

{markdown_table(validation)}

## Service-time statistics

{markdown_table(service_summary)}

## Payment-method comparison

{markdown_table(payment_summary)}

## Peak and non-peak comparison

{markdown_table(period_summary)}

## Weekday and weekend comparison

{markdown_table(week_summary)}

## Hourly service-time summary

{markdown_table(hourly_summary)}

## Day-of-week summary

Day numbering uses `0 = Monday` through `6 = Sunday`.

{markdown_table(day_summary)}

## Main statistical findings

- Correlation between item count and service time:
  **{correlation:.3f}**
- Peak-hour mean increase:
  **{peak_mean - non_peak_mean:.2f} seconds**
- Weekend mean increase:
  **{weekend_mean - weekday_mean:.2f} seconds**
- Payment methods have different average service times because the
  simulation assigns different payment overheads.
- These findings describe the simulation design and do not prove
  real-world checkout behaviour.
"""

    REPORT_PATH.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    REPORT_PATH.write_text(
        report,
        encoding="utf-8",
    )

    print(report)
    print(f"Saved analysis report at {REPORT_PATH}")


if __name__ == "__main__":
    main()
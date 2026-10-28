"""Compare the rule baseline and regression models, then save the best model."""

from datetime import datetime, timezone
from pathlib import Path

import joblib
import pandas as pd
from sklearn.base import clone
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import (
    mean_absolute_error,
    mean_squared_error,
    r2_score,
)
from sklearn.model_selection import (
    KFold,
    cross_val_score,
    train_test_split,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from sklearn.tree import DecisionTreeRegressor

from analyze_data import (
    PEAK_HOURS,
    build_validation_summary,
    load_data,
)


PROJECT_ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = PROJECT_ROOT / "ml" / "checkout_model.joblib"
RESULTS_PATH = PROJECT_ROOT / "docs" / "MODEL_RESULTS.md"

FEATURE_COLUMNS = [
    "item_count",
    "payment_method",
    "hour_of_day",
    "day_of_week",
]


def build_pipeline(regressor) -> Pipeline:
    categorical_features = [
        "payment_method",
        "hour_of_day",
        "day_of_week",
    ]

    transformer = ColumnTransformer(
        [
            (
                "categorical",
                OneHotEncoder(
                    handle_unknown="ignore"
                ),
                categorical_features,
            ),
        ],
        remainder="passthrough",
    )

    return Pipeline(
        [
            ("features", transformer),
            ("regressor", regressor),
        ]
    )


def rule_baseline(frame: pd.DataFrame) -> pd.Series:
    """Reproduce the current Spring Boot rule-based predictor."""

    payment_overhead = (
        frame["payment_method"]
        .map(
            {
                "upi": 8,
                "card": 15,
                "cash": 25,
            }
        )
        .fillna(15)
    )

    peak_overhead = (
        frame["hour_of_day"]
        .isin(PEAK_HOURS)
        .astype(int)
        * 14
    )

    prediction = (
        32
        + frame["item_count"] * 5
        + payment_overhead
        + peak_overhead
    )

    return prediction.clip(lower=45, upper=900)


def calculate_metrics(
    actual: pd.Series,
    predicted,
) -> dict:
    return {
        "mae": mean_absolute_error(actual, predicted),
        "rmse": mean_squared_error(actual, predicted) ** 0.5,
        "r2": r2_score(actual, predicted),
    }


def format_cv_value(value) -> str:
    if value is None:
        return "-"

    return f"{value:.2f}"


def main() -> None:
    data = load_data()
    validation = build_validation_summary(data)

    validation_errors = int(
        validation.loc[
            validation["Severity"] == "ERROR",
            "Problem rows",
        ].sum()
    )

    if validation_errors > 0:
        raise SystemExit(
            "Dataset validation failed. "
            "Run scripts/analyze_data.py for details."
        )

    original_rows = len(data)

    # Prevent identical records from appearing in both train and test sets.
    data = data.drop_duplicates().reset_index(drop=True)

    removed_duplicates = original_rows - len(data)

    features = data[FEATURE_COLUMNS]
    target = data["service_seconds"]

    x_train, x_test, y_train, y_test = train_test_split(
        features,
        target,
        test_size=0.2,
        random_state=42,
    )

    cross_validation = KFold(
        n_splits=5,
        shuffle=True,
        random_state=42,
    )

    candidates = {
        "Linear Regression": LinearRegression(),
        "Decision Tree": DecisionTreeRegressor(
            max_depth=8,
            min_samples_leaf=4,
            random_state=42,
        ),
        "Random Forest": RandomForestRegressor(
            n_estimators=200,
            max_depth=10,
            min_samples_leaf=3,
            random_state=42,
            n_jobs=-1,
        ),
    }

    results = []
    trained_models = {}

    for name, estimator in candidates.items():
        pipeline = build_pipeline(estimator)

        cv_mae_scores = -cross_val_score(
            pipeline,
            x_train,
            y_train,
            cv=cross_validation,
            scoring="neg_mean_absolute_error",
        )

        pipeline.fit(x_train, y_train)
        prediction = pipeline.predict(x_test)

        test_metrics = calculate_metrics(
            y_test,
            prediction,
        )

        results.append(
            {
                "name": name,
                "type": "ML",
                "cv_mae": cv_mae_scores.mean(),
                "cv_std": cv_mae_scores.std(),
                "test_mae": test_metrics["mae"],
                "test_rmse": test_metrics["rmse"],
                "test_r2": test_metrics["r2"],
            }
        )

        trained_models[name] = pipeline

        baseline_cv_mae_scores = []

    for _, validation_indexes in cross_validation.split(x_train):
        validation_features = x_train.iloc[
            validation_indexes
        ]

        validation_target = y_train.iloc[
            validation_indexes
        ]

        validation_prediction = rule_baseline(
            validation_features
        )

        baseline_cv_mae_scores.append(
            mean_absolute_error(
                validation_target,
                validation_prediction,
            )
        )

    baseline_cv_scores = pd.Series(
        baseline_cv_mae_scores
    )

    baseline_prediction = rule_baseline(x_test)

    baseline_metrics = calculate_metrics(
        y_test,
        baseline_prediction,
    )

    results.append(
        {
            "name": "Rule Baseline V1",
            "type": "RULE",
            "cv_mae": baseline_cv_scores.mean(),
            "cv_std": baseline_cv_scores.std(ddof=0),
            "test_mae": baseline_metrics["mae"],
            "test_rmse": baseline_metrics["rmse"],
            "test_r2": baseline_metrics["r2"],
        }
    )

    ml_results = [
        result
        for result in results
        if result["type"] == "ML"
    ]

    # Select using cross-validation, not the final test set.
    best = min(
        ml_results,
        key=lambda result: result["cv_mae"],
    )

    final_model = clone(
        trained_models[best["name"]]
    )

    # Retrain the selected model on every validated record.
    final_model.fit(features, target)

    MODEL_PATH.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    joblib.dump(
        {
            "model": final_model,
            "model_name": best["name"],
            "feature_columns": FEATURE_COLUMNS,
            "cross_validation_mae_seconds": round(
                best["cv_mae"],
                3,
            ),
            "validation_mae_seconds": round(
                best["test_mae"],
                3,
            ),
            "rule_baseline_mae_seconds": round(
                baseline_metrics["mae"],
                3,
            ),
            "training_rows": len(data),
            "removed_duplicate_rows": removed_duplicates,
            "training_data": "synthetic_simulation",
            "trained_at_utc": datetime.now(
                timezone.utc
            ).isoformat(),
        },
        MODEL_PATH,
    )

    sorted_results = sorted(
        results,
        key=lambda result: result["test_mae"],
    )

    rows = "\n".join(
        (
            f"| {result['name']} "
            f"| {format_cv_value(result['cv_mae'])} "
            f"| {format_cv_value(result['cv_std'])} "
            f"| {result['test_mae']:.2f} "
            f"| {result['test_rmse']:.2f} "
            f"| {result['test_r2']:.3f} |"
        )
        for result in sorted_results
    )

    report = f"""# Model Comparison

These results use **synthetic simulation data**, not observations from a
real store. They validate the pipeline only. Real-world performance claims
require permission-based checkout observations.

## Dataset preparation

- Original records: **{original_rows}**
- Exact duplicates removed: **{removed_duplicates}**
- Training and validation records: **{len(x_train)}**
- Independent test records: **{len(x_test)}**
- Cross-validation folds: **5**

## Results

| Model | CV MAE | CV standard deviation | Test MAE | Test RMSE | Test R² |
|---|---:|---:|---:|---:|---:|
{rows}

## Selection

Selected model: **{best['name']}**

Among the three ML candidates, the model was selected using the lowest five-fold cross-validation MAE:
**{best['cv_mae']:.2f} seconds**.

Its independent test MAE was **{best['test_mae']:.2f} seconds**.

The existing Spring Boot rule baseline achieved a test MAE of
**{baseline_metrics['mae']:.2f} seconds**.

The selected model was retrained using all validated, deduplicated records
and saved for prediction-service integration.
"""

    RESULTS_PATH.write_text(
        report,
        encoding="utf-8",
    )

    print(report)
    print(f"Saved model at {MODEL_PATH}")


if __name__ == "__main__":
    main()
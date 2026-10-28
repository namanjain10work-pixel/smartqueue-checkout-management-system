"""Small ML-only API used by the Spring Boot backend."""

from datetime import datetime

from fastapi import FastAPI
from pydantic import BaseModel, Field, field_validator

from app.services.predictor import (
    model_status,
    predict_service_time,
)


app = FastAPI(
    title="SmartQueue ML Prediction Service",
    version="1.0.0",
    description=(
        "Provides checkout service-time predictions to "
        "the main Spring Boot application."
    ),
)


class PredictionRequest(BaseModel):
    item_count: int = Field(
        ge=1,
        le=120,
    )

    payment_method: str

    observed_at: datetime | None = None

    @field_validator("payment_method")
    @classmethod
    def validate_payment_method(
        cls,
        value: str,
    ) -> str:
        normalized = value.strip().lower()

        allowed_methods = {
            "upi",
            "card",
            "cash",
        }

        if normalized not in allowed_methods:
            raise ValueError(
                "payment_method must be upi, card, or cash"
            )

        return normalized


class PredictionResponse(BaseModel):
    predicted_service_seconds: int
    prediction_source: str


@app.get("/health")
def health() -> dict:
    return {
        "status": "UP",
        "service": "SmartQueue ML Prediction Service",
    }


@app.get("/api/ml/status")
def status() -> dict:
    return model_status()


@app.post(
    "/api/ml/predict",
    response_model=PredictionResponse,
)
def predict(
    payload: PredictionRequest,
) -> PredictionResponse:
    seconds, source = predict_service_time(
        item_count=payload.item_count,
        payment_method=payload.payment_method,
        moment=payload.observed_at,
    )

    return PredictionResponse(
        predicted_service_seconds=seconds,
        prediction_source=source,
    )
package com.smartqueue.service;

import com.smartqueue.model.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;

@Service
public class CheckoutTimePredictor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    CheckoutTimePredictor.class
            );

    private final RestClient mlRestClient;
    private final boolean mlEnabled;

    public CheckoutTimePredictor(
            @Value(
                    "${smartqueue.ml.base-url:" +
                            "http://127.0.0.1:8001}"
            )
            String mlBaseUrl,
            @Value("${smartqueue.ml.enabled:false}")
            boolean mlEnabled
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.mlRestClient = RestClient.builder()
                .requestFactory(
                        new JdkClientHttpRequestFactory(httpClient)
                )
                .baseUrl(mlBaseUrl)
                .build();

        this.mlEnabled = mlEnabled;
    }

    public Prediction predict(
            int itemCount,
            PaymentMethod paymentMethod
    ) {
        LocalDateTime predictionTime =
                LocalDateTime.now();

        if (mlEnabled) {
            Prediction mlPrediction =
                    predictWithMl(
                            itemCount,
                            paymentMethod,
                            predictionTime
                    );

            if (mlPrediction != null) {
                return mlPrediction;
            }
        }

        return predictWithRule(
                itemCount,
                paymentMethod,
                predictionTime
        );
    }

    private Prediction predictWithMl(
            int itemCount,
            PaymentMethod paymentMethod,
            LocalDateTime predictionTime
    ) {
        try {
            MlPredictionResponse response =
                    mlRestClient
                            .post()
                            .uri("/api/ml/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(
                                    new MlPredictionRequest(
                                            itemCount,
                                            paymentMethod
                                                    .name()
                                                    .toLowerCase(
                                                            Locale.ROOT
                                                    ),
                                            predictionTime
                                    )
                            )
                            .retrieve()
                            .body(
                                    MlPredictionResponse.class
                            );

            if (response == null) {
                return null;
            }

            int limitedPrediction =
                    limitPrediction(
                            response
                                    .predicted_service_seconds()
                    );

            String modelName =
                    response.prediction_source();

            if (modelName == null
                    || modelName.isBlank()
                    || "RULE_BASELINE_V1".equalsIgnoreCase(modelName.trim())) {
                return null;
            }

            String source =
                    "ML_" +
                            modelName
                                    .trim()
                                    .toUpperCase(Locale.ROOT)
                                    .replaceAll(
                                            "[^A-Z0-9]+",
                                            "_"
                                    );

            return new Prediction(
                    limitedPrediction,
                    source
            );

        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "ML prediction unavailable; " +
                            "using rule fallback. Cause: {}",
                    exception.getMessage()
            );

            return null;
        }
    }

    private Prediction predictWithRule(
            int itemCount,
            PaymentMethod paymentMethod,
            LocalDateTime predictionTime
    ) {
        int baseSeconds = 32;
        int itemProcessingSeconds =
                itemCount * 5;

        int paymentSeconds =
                switch (paymentMethod) {
                    case UPI -> 8;
                    case CARD -> 15;
                    case CASH -> 25;
                };

        int currentHour =
                predictionTime.getHour();

        boolean isPeakHour =
                currentHour == 12 ||
                        currentHour == 13 ||
                        currentHour >= 17 &&
                                currentHour <= 20;

        int peakHourSeconds =
                isPeakHour ? 14 : 0;

        int prediction =
                baseSeconds +
                        itemProcessingSeconds +
                        paymentSeconds +
                        peakHourSeconds;

        return new Prediction(
                limitPrediction(prediction),
                "RULE_BASELINE_V1"
        );
    }

    private int limitPrediction(int prediction) {
        return Math.max(
                45,
                Math.min(prediction, 900)
        );
    }

    private record MlPredictionRequest(
            int item_count,
            String payment_method,
            LocalDateTime observed_at
    ) {
    }

    private record MlPredictionResponse(
            int predicted_service_seconds,
            String prediction_source
    ) {
    }

    public record Prediction(
            int seconds,
            String source
    ) {
    }
}

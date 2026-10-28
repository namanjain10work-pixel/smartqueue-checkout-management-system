package com.smartqueue.service;

import com.smartqueue.model.PaymentMethod;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutTimePredictorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesRulePredictionWhenMlIsDisabled() {

        CheckoutTimePredictor predictor =
                new CheckoutTimePredictor(
                        "http://127.0.0.1:8001",
                        false
                );

        CheckoutTimePredictor.Prediction prediction =
                predictor.predict(15, PaymentMethod.UPI);

        assertEquals(
                "RULE_BASELINE_V1",
                prediction.source()
        );

        assertTrue(prediction.seconds() >= 45);
        assertTrue(prediction.seconds() <= 900);
    }

    @Test
    void usesMlPredictionWhenMlServiceResponds()
            throws IOException {

        AtomicReference<String> receivedBody =
                new AtomicReference<>();

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext(
                "/api/ml/predict",
                exchange -> {

                    String requestBody = new String(
                            exchange
                                    .getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    );

                    receivedBody.set(requestBody);

                    byte[] response = """
                            {
                              "predicted_service_seconds": 123,
                              "prediction_source": "Linear Regression"
                            }
                            """.getBytes(StandardCharsets.UTF_8);

                    exchange
                            .getResponseHeaders()
                            .set(
                                    "Content-Type",
                                    "application/json"
                            );

                    exchange.sendResponseHeaders(
                            200,
                            response.length
                    );

                    try (var output =
                                 exchange.getResponseBody()) {
                        output.write(response);
                    }
                }
        );

        server.start();

        String baseUrl =
                "http://127.0.0.1:" +
                        server.getAddress().getPort();

        CheckoutTimePredictor predictor =
                new CheckoutTimePredictor(
                        baseUrl,
                        true
                );

        CheckoutTimePredictor.Prediction prediction =
                predictor.predict(15, PaymentMethod.UPI);

        assertEquals(123, prediction.seconds());

        assertEquals(
                "ML_LINEAR_REGRESSION",
                prediction.source()
        );

        assertNotNull(receivedBody.get());

        assertTrue(
                receivedBody.get().contains(
                        "\"item_count\""
                )
        );

        assertTrue(
                receivedBody.get().contains(
                        "\"payment_method\""
                )
        );

        assertTrue(
                receivedBody.get().contains(
                        "\"upi\""
                )
        );
    }

    @Test
    void fallsBackToRuleWhenMlServiceFails()
            throws IOException {

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext(
                "/api/ml/predict",
                exchange -> {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                }
        );

        server.start();

        String baseUrl =
                "http://127.0.0.1:" +
                        server.getAddress().getPort();

        CheckoutTimePredictor predictor =
                new CheckoutTimePredictor(
                        baseUrl,
                        true
                );

        CheckoutTimePredictor.Prediction prediction =
                predictor.predict(15, PaymentMethod.UPI);

        assertEquals(
                "RULE_BASELINE_V1",
                prediction.source()
        );

        assertTrue(prediction.seconds() >= 45);
        assertTrue(prediction.seconds() <= 900);
    }
}

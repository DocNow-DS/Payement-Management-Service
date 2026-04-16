package com.healthcare.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class NotificationServiceClient {

    private final RestTemplate restTemplate;
    private final String notificationServiceBaseUrl;

    public NotificationServiceClient(
            RestTemplate restTemplate,
            @Value("${services.notification.base-url:http://localhost:8085}") String notificationServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.notificationServiceBaseUrl = notificationServiceBaseUrl;
    }

    public void sendPaymentNotification(String patientId, String doctorId, String paymentId, String consultationId, Long amountCents, String currency, String token) {
        try {
            String url = notificationServiceBaseUrl + "/api/notifications/payment";
            log.info("Sending payment notification to: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", "Bearer " + token);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("patientId", patientId);
            requestBody.put("doctorId", doctorId);
            requestBody.put("paymentId", paymentId);
            requestBody.put("consultationId", consultationId);
            requestBody.put("amountCents", amountCents);
            requestBody.put("currency", currency);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Payment notification sent successfully for payment: {}", paymentId);
            } else {
                log.warn("Payment notification failed with status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending payment notification for payment {}: {}", paymentId, e.getMessage());
            // Don't throw exception - notification failure shouldn't break payment flow
        }
    }
}

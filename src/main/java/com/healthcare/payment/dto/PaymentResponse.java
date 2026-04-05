package com.healthcare.payment.dto;

import com.healthcare.payment.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String id;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    private String consultationId;
    private String patientId;
    private String customerEmail;
    private Long amountCents;
    private String currency;
    private PaymentStatus status;
    private Instant createdAt;
}

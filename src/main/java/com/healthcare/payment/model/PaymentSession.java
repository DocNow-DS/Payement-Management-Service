package com.healthcare.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payment_sessions")
public class PaymentSession {

    @Id
    private String id;

    @Indexed(unique = true)
    private String stripeSessionId;

    private String stripePaymentIntentId;

    @Indexed
    private String consultationId;

    @Indexed
    private String patientId;

    private String customerEmail;

    private Long amountCents;

    private String currency;

    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    private String successUrl;

    private String cancelUrl;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

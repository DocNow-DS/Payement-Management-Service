package com.healthcare.payment.service;

import com.healthcare.payment.dto.CheckoutRequest;
import com.healthcare.payment.dto.CheckoutResponse;
import com.healthcare.payment.dto.PaymentResponse;
import com.healthcare.payment.model.PaymentSession;
import com.healthcare.payment.model.PaymentStatus;
import com.healthcare.payment.repository.PaymentSessionRepository;
import com.healthcare.payment.stripe.StripeClientAdapter;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentSessionRepository paymentSessionRepository;
    private final StripeClientAdapter stripeClientAdapter;

    /**
     * Creates a Stripe Checkout Session and persists the payment record.
     */
    public CheckoutResponse createCheckoutSession(CheckoutRequest request, String patientId) throws StripeException {
        // Convert LKR to cents (1 LKR = 100 cents)
        long amountCents = request.getAmountLKR() * 100;

        // Create Stripe Checkout Session
        Session stripeSession = stripeClientAdapter.createCheckoutSession(
                amountCents,
                request.getCurrency() != null ? request.getCurrency() : "lkr",
                request.getConsultationId(),
                request.getCustomerEmail(),
                request.getSuccessUrl(),
                request.getCancelUrl()
        );

        // Persist payment session in MongoDB
        PaymentSession paymentSession = PaymentSession.builder()
                .stripeSessionId(stripeSession.getId())
                .consultationId(request.getConsultationId())
                .patientId(patientId)
                .customerEmail(request.getCustomerEmail())
                .amountCents(amountCents)
                .currency(request.getCurrency() != null ? request.getCurrency() : "lkr")
                .status(PaymentStatus.CREATED)
                .successUrl(request.getSuccessUrl())
                .cancelUrl(request.getCancelUrl())
                .build();

        paymentSessionRepository.save(paymentSession);
        log.info("Payment session created: {} for consultation: {}", paymentSession.getId(), request.getConsultationId());

        return CheckoutResponse.builder()
                .sessionId(stripeSession.getId())
                .checkoutUrl(stripeSession.getUrl())
                .build();
    }

    /**
     * Handles incoming Stripe webhook events.
     */
    public void handleWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        // Verify webhook signature
        Event event = stripeClientAdapter.constructWebhookEvent(payload, sigHeader);
        log.info("Received Stripe webhook event: {} (type: {})", event.getId(), event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "checkout.session.expired" -> handleCheckoutSessionExpired(event);
            case "payment_intent.succeeded" -> log.info("Payment intent succeeded: {}", event.getId());
            case "payment_intent.payment_failed" -> handlePaymentFailed(event);
            default -> log.info("Unhandled event type: {}", event.getType());
        }
    }

    /**
     * Retrieves a payment by its internal ID.
     */
    public PaymentResponse getPaymentById(String paymentId) {
        PaymentSession session = paymentSessionRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        return mapToResponse(session);
    }

    /**
     * Retrieves a payment by its consultation ID.
     */
    public PaymentResponse getPaymentByConsultationId(String consultationId) {
        PaymentSession session = paymentSessionRepository.findByConsultationId(consultationId)
                .orElseThrow(() -> new RuntimeException("Payment not found for consultation: " + consultationId));
        return mapToResponse(session);
    }

    /**
     * Retrieves a payment by its Stripe Checkout session ID.
     */
    public PaymentResponse getPaymentByStripeSessionId(String stripeSessionId) {
        PaymentSession session = paymentSessionRepository.findByStripeSessionId(stripeSessionId)
                .orElseThrow(() -> new RuntimeException("Payment not found for Stripe session: " + stripeSessionId));
        return mapToResponse(session);
    }

    /**
     * Retrieves all payments for a specific patient.
     */
    public List<PaymentResponse> getPaymentsByPatientId(String patientId) {
        return paymentSessionRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public String getStripePublishableKey() {
        return stripeClientAdapter.getPublishableKey();
    }

    // ─── Private helpers ─────────────────────────────────────────

    private void handleCheckoutSessionCompleted(Event event) {
        Optional<StripeObject> stripeObject = extractStripeObject(event);
        if (stripeObject.isPresent() && stripeObject.get() instanceof Session session) {
            String stripeSessionId = session.getId();
            String paymentIntentId = session.getPaymentIntent();

            paymentSessionRepository.findByStripeSessionId(stripeSessionId).ifPresent(paymentSession -> {
                paymentSession.setStatus(PaymentStatus.COMPLETED);
                paymentSession.setStripePaymentIntentId(paymentIntentId);
                paymentSessionRepository.save(paymentSession);
                log.info("Payment COMPLETED for consultation: {}", paymentSession.getConsultationId());

                // TODO: Call appointment/consultation service to mark consultation as "paid"
                // e.g., restTemplate.patchForObject(appointmentServiceUrl + "/api/patient/appointments/" + paymentSession.getConsultationId() + "/status", ...)
            });
        }
    }

    private void handleCheckoutSessionExpired(Event event) {
        Optional<StripeObject> stripeObject = extractStripeObject(event);
        if (stripeObject.isPresent() && stripeObject.get() instanceof Session session) {
            paymentSessionRepository.findByStripeSessionId(session.getId()).ifPresent(paymentSession -> {
                paymentSession.setStatus(PaymentStatus.EXPIRED);
                paymentSessionRepository.save(paymentSession);
                log.info("Payment session EXPIRED for consultation: {}", paymentSession.getConsultationId());
            });
        }
    }

    private void handlePaymentFailed(Event event) {
        log.warn("Payment failed event received: {}", event.getId());
        // Payment intents don't directly map to sessions via the same field,
        // but you can extract the payment_intent ID and update accordingly if needed.
    }

    private Optional<StripeObject> extractStripeObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject().map(obj -> obj);
        }
        log.warn("Unable to deserialize Stripe event data for event: {}", event.getId());
        return Optional.empty();
    }

    private PaymentResponse mapToResponse(PaymentSession session) {
        return PaymentResponse.builder()
                .id(session.getId())
                .stripeSessionId(session.getStripeSessionId())
                .consultationId(session.getConsultationId())
                .patientId(session.getPatientId())
                .customerEmail(session.getCustomerEmail())
                .amountCents(session.getAmountCents())
                .currency(session.getCurrency())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .build();
    }
}

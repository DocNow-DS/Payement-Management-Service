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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long MIN_CHECKOUT_LKR = 200L;

    private final PaymentSessionRepository paymentSessionRepository;
    private final StripeClientAdapter stripeClientAdapter;
    private final RestTemplate restTemplate;

    @Value("${doctor.service.url:http://localhost:8082}")
    private String doctorServiceUrl;

    /**
     * Creates a Stripe Checkout Session and persists the payment record.
     */
    public CheckoutResponse createCheckoutSession(CheckoutRequest request, String patientId) throws StripeException {
        long requestedAmount = request.getAmountLKR() == null ? 0L : request.getAmountLKR();
        if (requestedAmount < MIN_CHECKOUT_LKR) {
            throw new RuntimeException("Minimum payable amount is " + MIN_CHECKOUT_LKR + " LKR for gateway checkout");
        }

        // Convert LKR to cents (1 LKR = 100 cents)
        long amountCents = requestedAmount * 100;

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
     * Confirms and synchronizes payment state from Stripe checkout session.
     * Useful right after redirect when webhook delivery is delayed.
     */
    public PaymentResponse confirmPaymentByStripeSessionId(String stripeSessionId, String patientId) throws StripeException {
        PaymentSession session = paymentSessionRepository.findByStripeSessionId(stripeSessionId)
                .orElseThrow(() -> new RuntimeException("Payment not found for Stripe session: " + stripeSessionId));

        if (patientId != null && !patientId.isBlank() && !patientId.equals(session.getPatientId())) {
            throw new RuntimeException("Access denied for this payment session");
        }

        Session stripeSession = stripeClientAdapter.retrieveSession(stripeSessionId);
        String stripePaymentStatus = stripeSession.getPaymentStatus();
        String stripeSessionStatus = stripeSession.getStatus();
        String paymentIntentId = stripeSession.getPaymentIntent();

        log.info("Confirm endpoint: Session {} has payment_status={}, session_status={}, intent={}",
                stripeSessionId, stripePaymentStatus, stripeSessionStatus, paymentIntentId);

        if ("paid".equalsIgnoreCase(stripePaymentStatus)) {
            session.setStatus(PaymentStatus.COMPLETED);
            if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                session.setStripePaymentIntentId(paymentIntentId);
            }
        } else if ("expired".equalsIgnoreCase(stripeSessionStatus)) {
            session.setStatus(PaymentStatus.EXPIRED);
        }

        PaymentSession saved = paymentSessionRepository.save(session);

        // Notify doctor service that related care-plan/consultation is paid
        if (saved.getStatus() == PaymentStatus.COMPLETED) {
            notifyDoctorServiceAboutPayment(saved);
        }

        return mapToResponse(saved);
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
            String paymentStatus = session.getPaymentStatus();
            String paymentIntentId = session.getPaymentIntent();

            log.info("Webhook: Session {} has payment_status={}, payment_intent={}",
                    stripeSessionId, paymentStatus, paymentIntentId);

            paymentSessionRepository.findByStripeSessionId(stripeSessionId).ifPresent(paymentSession -> {
                // Only mark as completed if payment actually succeeded
                if ("paid".equalsIgnoreCase(paymentStatus)) {
                    paymentSession.setStatus(PaymentStatus.COMPLETED);
                    if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                        paymentSession.setStripePaymentIntentId(paymentIntentId);
                    }
                    paymentSessionRepository.save(paymentSession);
                    log.info("Payment COMPLETED for consultation: {} with intent: {}",
                            paymentSession.getConsultationId(), paymentIntentId);

                    // Notify doctor service
                    notifyDoctorServiceAboutPayment(paymentSession);
                } else {
                    log.warn("Webhook: payment_status is not 'paid', status={}", paymentStatus);
                }
            });
        }

    }

    private void notifyDoctorServiceAboutPayment(PaymentSession paymentSession) {
        try {
            String consultationId = paymentSession.getConsultationId();
            if (consultationId != null && !consultationId.isBlank()) {
                String url = doctorServiceUrl + "/api/internal/care-plans/" + consultationId + "/mark-paid";
                try {
                    restTemplate.postForEntity(url, null, String.class);
                    log.info("Successfully notified doctor service to mark care plan paid: {}", consultationId);
                } catch (Exception e) {
                    log.warn("Doctor service notification failed for {}: {} - {}",
                            consultationId, e.getClass().getSimpleName(), e.getMessage());
                }
            } else {
                log.warn("Payment session {} has no consultationId, skipping doctor service notification", paymentSession.getId());
            }
        } catch (Exception e) {
            log.error("Unexpected error notifying doctor service about payment: {}", e.getMessage(), e);
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
                .stripePaymentIntentId(session.getStripePaymentIntentId())
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

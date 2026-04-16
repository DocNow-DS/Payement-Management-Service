package com.healthcare.payment.controller;

import com.healthcare.payment.dto.CheckoutRequest;
import com.healthcare.payment.dto.CheckoutResponse;
import com.healthcare.payment.dto.PaymentResponse;
import com.healthcare.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /api/v1/payments/checkout-session
     * Creates a Stripe Checkout Session. Requires JWT authentication.
     */
    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication) throws StripeException {

        String patientId = authentication.getName();
        log.info("Creating checkout session for patient: {}, consultation: {}", patientId, request.getConsultationId());

        CheckoutResponse response = paymentService.createCheckoutSession(request, patientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/payments/webhook
     * Receives Stripe webhook events. No JWT auth — secured via Stripe signature verification.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            paymentService.handleWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok("Webhook processed");
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing error");
        }
    }

    /**
     * GET /api/v1/payments/{paymentId}
     * Retrieves a payment session by its internal ID.
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/payments/consultation/{consultationId}
     * Retrieves a payment session by consultation ID.
     */
    @GetMapping("/consultation/{consultationId}")
    public ResponseEntity<PaymentResponse> getPaymentByConsultation(@PathVariable String consultationId) {
        PaymentResponse response = paymentService.getPaymentByConsultationId(consultationId);
        return ResponseEntity.ok(response);
    }

    /**

     * GET /api/v1/payments/session/{sessionId}
     * Retrieves a payment session by Stripe checkout session ID.
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<PaymentResponse> getPaymentBySession(@PathVariable String sessionId) {
        PaymentResponse response = paymentService.getPaymentByStripeSessionId(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/payments/session/{sessionId}/confirm
     * Forces a sync with Stripe to confirm payment status after redirect.
     */
    @PostMapping("/session/{sessionId}/confirm")
    public ResponseEntity<PaymentResponse> confirmPaymentBySession(@PathVariable String sessionId,
                                                                   Authentication authentication) throws StripeException {
        String patientId = authentication.getName();
        PaymentResponse response = paymentService.confirmPaymentByStripeSessionId(sessionId, patientId);
        return ResponseEntity.ok(response);
    }

    /**

     * GET /api/v1/payments/patient/my-payments
     * Retrieves all payments for the authenticated patient.
     */
    @GetMapping("/patient/my-payments")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(Authentication authentication) {
        String patientId = authentication.getName();
        List<PaymentResponse> payments = paymentService.getPaymentsByPatientId(patientId);
        return ResponseEntity.ok(payments);
    }

    /**
     * GET /api/v1/payments/stripe-config
     * Returns Stripe frontend configuration.
     */
    @GetMapping("/stripe-config")
    public ResponseEntity<Map<String, String>> getStripeConfig() {
        return ResponseEntity.ok(Map.of("publishableKey", paymentService.getStripePublishableKey()));
    }
}

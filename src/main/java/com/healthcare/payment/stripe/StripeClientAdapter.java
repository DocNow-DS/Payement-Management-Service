package com.healthcare.payment.stripe;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StripeClientAdapter {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.publishable-key}")
    private String stripePublishableKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        validateSecretKey();
        Stripe.apiKey = stripeSecretKey;
        log.info("Stripe API initialized");
    }

    /**
     * Creates a Stripe Checkout Session for one-time payment.
     */
    public Session createCheckoutSession(
            Long amountCents,
            String currency,
            String consultationId,
            String customerEmail,
            String successUrl,
            String cancelUrl
    ) throws StripeException {

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(customerEmail)
                .setClientReferenceId(consultationId)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(currency)
                                                .setUnitAmount(amountCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Healthcare Consultation Payment")
                                                                .setDescription("Payment for consultation: " + consultationId)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe checkout session: {} for consultation: {}", session.getId(), consultationId);
        return session;
    }

    /**
     * Verifies and constructs a Stripe webhook event from the raw payload.
     */
    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        validateWebhookSecret();
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    /**
     * Retrieves a Stripe Checkout Session by its ID.
     */
    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    public String getPublishableKey() {
        validatePublishableKey();
        return stripePublishableKey;
    }

    private void validateSecretKey() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank() || stripeSecretKey.contains("your_stripe_test_key_here") || !stripeSecretKey.startsWith("sk_")) {
            throw new IllegalStateException("Invalid Stripe secret key. Set STRIPE_SECRET_KEY with a valid value.");
        }
    }

    private void validatePublishableKey() {
        if (stripePublishableKey == null || stripePublishableKey.isBlank() || stripePublishableKey.contains("your_stripe_publishable_key_here") || !stripePublishableKey.startsWith("pk_")) {
            throw new IllegalStateException("Invalid Stripe publishable key. Set STRIPE_PUBLISHABLE_KEY with a valid value.");
        }
    }

    private void validateWebhookSecret() {
        if (webhookSecret == null || webhookSecret.isBlank() || webhookSecret.contains("your_webhook_secret_here") || !webhookSecret.startsWith("whsec_")) {
            throw new IllegalStateException("Invalid Stripe webhook secret. Set STRIPE_WEBHOOK_SECRET with a valid value.");
        }
    }
}

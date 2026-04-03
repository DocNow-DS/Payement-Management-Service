package com.healthcare.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public class CheckoutRequest {

    @NotNull(message = "Amount is required")
    @Min(value = 200, message = "Amount must be at least 200 LKR")
    private Long amountLKR;

    private String currency = "lkr";

    @NotBlank(message = "Consultation ID is required")
    private String consultationId;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;

    @NotBlank(message = "Success URL is required")
    private String successUrl;

    @NotBlank(message = "Cancel URL is required")
    private String cancelUrl;

    public CheckoutRequest() {
    }

    public CheckoutRequest(Long amountLKR,
                           String currency,
                           String consultationId,
                           String customerEmail,
                           String successUrl,
                           String cancelUrl) {
        this.amountLKR = amountLKR;
        this.currency = currency;
        this.consultationId = consultationId;
        this.customerEmail = customerEmail;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    public Long getAmountLKR() {
        return amountLKR;
    }

    public void setAmountLKR(Long amountLKR) {
        this.amountLKR = amountLKR;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getConsultationId() {
        return consultationId;
    }

    public void setConsultationId(String consultationId) {
        this.consultationId = consultationId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}

package com.tests.models;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Example domain model for a payment-initiation request — mirrors the kind of payload
 * you'd build for Razorpay/AutoPay style flows instead of hand-writing raw JSON strings in tests.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentRequest {

    private String orderId;
    private long amountInPaise;
    private String currency;
    private String paymentMethod;
    private String customerId;
    private boolean autopayEnabled;

    private PaymentRequest(Builder builder) {
        this.orderId = builder.orderId;
        this.amountInPaise = builder.amountInPaise;
        this.currency = builder.currency;
        this.paymentMethod = builder.paymentMethod;
        this.customerId = builder.customerId;
        this.autopayEnabled = builder.autopayEnabled;
    }

    public String getOrderId() {
        return orderId;
    }

    public long getAmountInPaise() {
        return amountInPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getCustomerId() {
        return customerId;
    }

    public boolean isAutopayEnabled() {
        return autopayEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String orderId;
        private long amountInPaise;
        private String currency = "INR";
        private String paymentMethod;
        private String customerId;
        private boolean autopayEnabled = false;

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder amountInPaise(long amountInPaise) {
            this.amountInPaise = amountInPaise;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder autopayEnabled(boolean autopayEnabled) {
            this.autopayEnabled = autopayEnabled;
            return this;
        }

        public PaymentRequest build() {
            return new PaymentRequest(this);
        }
    }
}

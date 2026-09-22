package com.tests.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /payments/processRazorpayPayment body:
 * {@code {"payment_id":<paymentId>,"razorpay_payment_id":"pay_..."}}. The {@code razorpay_payment_id}
 * only exists once Razorpay itself has actually processed a card/UPI charge — this framework has
 * no way to obtain a real one purely at the API level (that needs either Razorpay API keys this
 * project doesn't have, or driving the Razorpay checkout UI as {@code FurlencoCheckoutFlowTest}
 * already does). This model exists only to exercise the negative path: submitting a bogus
 * reference and confirming the API rejects it rather than silently marking the order paid.
 */
public class ProcessRazorpayPaymentRequest {

    @JsonProperty("payment_id")
    private long paymentId;

    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    private ProcessRazorpayPaymentRequest(Builder builder) {
        this.paymentId = builder.paymentId;
        this.razorpayPaymentId = builder.razorpayPaymentId;
    }

    public long getPaymentId() {
        return paymentId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long paymentId;
        private String razorpayPaymentId;

        public Builder paymentId(long paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder razorpayPaymentId(String razorpayPaymentId) {
            this.razorpayPaymentId = razorpayPaymentId;
            return this;
        }

        public ProcessRazorpayPaymentRequest build() {
            return new ProcessRazorpayPaymentRequest(this);
        }
    }
}

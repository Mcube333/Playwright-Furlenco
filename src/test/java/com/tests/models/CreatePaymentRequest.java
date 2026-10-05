package com.tests.models;

/**
 * POST /carts/{cartId}/createPayment body: {@code {"gstin":""}}. An empty string is what the real
 * app sends for a consumer (non-GST) purchase.
 */
public class CreatePaymentRequest {

    private String gstin;

    private CreatePaymentRequest(Builder builder) {
        this.gstin = builder.gstin;
    }

    public String getGstin() {
        return gstin;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String gstin = "";

        public Builder gstin(String gstin) {
            this.gstin = gstin;
            return this;
        }

        public CreatePaymentRequest build() {
            return new CreatePaymentRequest(this);
        }
    }
}

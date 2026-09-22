package com.tests.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /carts/{cartId}/create-checkout-order body. The real app also sends a
 * {@code deviceParameters} block (Singular/IDFA attribution data) alongside
 * {@code isAutopaySelected}, but that's purely attribution metadata — deliberately left out here
 * as an unnecessary complication rather than something the order-creation contract needs.
 */
public class CheckoutOrderRequest {

    @JsonProperty("isAutopaySelected")
    private boolean isAutopaySelected;

    private CheckoutOrderRequest(Builder builder) {
        this.isAutopaySelected = builder.isAutopaySelected;
    }

    public boolean isAutopaySelected() {
        return isAutopaySelected;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean isAutopaySelected = false;

        public Builder isAutopaySelected(boolean isAutopaySelected) {
            this.isAutopaySelected = isAutopaySelected;
            return this;
        }

        public CheckoutOrderRequest build() {
            return new CheckoutOrderRequest(this);
        }
    }
}

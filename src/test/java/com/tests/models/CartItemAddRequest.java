package com.tests.models;

/**
 * POST /carts/{cartId}/cartItems body: {@code {"tenure":<months>,"id":<catalogProductId>}}.
 */
public class CartItemAddRequest {

    private int tenure;
    private long id;

    private CartItemAddRequest(Builder builder) {
        this.tenure = builder.tenure;
        this.id = builder.id;
    }

    public int getTenure() {
        return tenure;
    }

    public long getId() {
        return id;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int tenure = 1;
        private long id;

        public Builder tenure(int tenure) {
            this.tenure = tenure;
            return this;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public CartItemAddRequest build() {
            return new CartItemAddRequest(this);
        }
    }
}

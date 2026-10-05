package com.tests.models;

/**
 * POST /users/verify-account — step 1 of Furlenco's phone+OTP login (mirrors the payload the iOS
 * app sends: {@code {"account":"<phone>"}}, triggering an OTP to that number).
 */
public class VerifyAccountRequest {

    private String account;

    private VerifyAccountRequest(Builder builder) {
        this.account = builder.account;
    }

    public String getAccount() {
        return account;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String account;

        public Builder account(String account) {
            this.account = account;
            return this;
        }

        public VerifyAccountRequest build() {
            return new VerifyAccountRequest(this);
        }
    }
}

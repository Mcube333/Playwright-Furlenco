package com.tests.models;

/**
 * POST /users/verify-otp — step 2 of Furlenco's phone+OTP login: {@code {"account":"<phone>",
 * "otp":"<otp>"}}. On staging, {@code otp} is the fixed test OTP configured under
 * {@code test.user.password} (see {@code FurlencoLoginPage} javadoc) — never a real OTP.
 */
public class VerifyOtpRequest {

    private String account;
    private String otp;

    private VerifyOtpRequest(Builder builder) {
        this.account = builder.account;
        this.otp = builder.otp;
    }

    public String getAccount() {
        return account;
    }

    public String getOtp() {
        return otp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String account;
        private String otp;

        public Builder account(String account) {
            this.account = account;
            return this;
        }

        public Builder otp(String otp) {
            this.otp = otp;
            return this;
        }

        public VerifyOtpRequest build() {
            return new VerifyOtpRequest(this);
        }
    }
}

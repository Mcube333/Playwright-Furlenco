package com.tests.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /kyc/verify-user body: {@code {"contact_no":"<phone>"}} (or {@code {"email_id":"..."}} for
 * the email-verification variant of this same endpoint — only the phone variant is modeled here).
 */
public class KycVerifyUserRequest {

    @JsonProperty("contact_no")
    private String contactNo;

    private KycVerifyUserRequest(Builder builder) {
        this.contactNo = builder.contactNo;
    }

    public String getContactNo() {
        return contactNo;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String contactNo;

        public Builder contactNo(String contactNo) {
            this.contactNo = contactNo;
            return this;
        }

        public KycVerifyUserRequest build() {
            return new KycVerifyUserRequest(this);
        }
    }
}

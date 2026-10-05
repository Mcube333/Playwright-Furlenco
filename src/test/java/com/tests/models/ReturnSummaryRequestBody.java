package com.tests.models;

/**
 * POST /returns/summary body: {@code {"userSelectionDetails":{...}}} — same
 * {@link ReturnUserSelectionDetails} shape as {@link ReturnRequestBody}, but this step's
 * {@code userSelectionDetails} additionally carries the pickup {@code addressId}.
 */
public class ReturnSummaryRequestBody {

    private ReturnUserSelectionDetails userSelectionDetails;

    private ReturnSummaryRequestBody(Builder builder) {
        this.userSelectionDetails = builder.userSelectionDetails;
    }

    public ReturnUserSelectionDetails getUserSelectionDetails() {
        return userSelectionDetails;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ReturnUserSelectionDetails userSelectionDetails;

        public Builder userSelectionDetails(ReturnUserSelectionDetails userSelectionDetails) {
            this.userSelectionDetails = userSelectionDetails;
            return this;
        }

        public ReturnSummaryRequestBody build() {
            return new ReturnSummaryRequestBody(this);
        }
    }
}

package com.tests.models;

/**
 * POST /returns/request body: {@code {"previousState":"PHOENIX","userSelectionDetails":{...}}}.
 * {@code previousState} is a fixed app-side FSM token ("PHOENIX") observed on every captured
 * request — not something a test chooses, so it defaults to that value.
 */
public class ReturnRequestBody {

    private String previousState;
    private ReturnUserSelectionDetails userSelectionDetails;

    private ReturnRequestBody(Builder builder) {
        this.previousState = builder.previousState;
        this.userSelectionDetails = builder.userSelectionDetails;
    }

    public String getPreviousState() {
        return previousState;
    }

    public ReturnUserSelectionDetails getUserSelectionDetails() {
        return userSelectionDetails;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String previousState = "PHOENIX";
        private ReturnUserSelectionDetails userSelectionDetails;

        public Builder previousState(String previousState) {
            this.previousState = previousState;
            return this;
        }

        public Builder userSelectionDetails(ReturnUserSelectionDetails userSelectionDetails) {
            this.userSelectionDetails = userSelectionDetails;
            return this;
        }

        public ReturnRequestBody build() {
            return new ReturnRequestBody(this);
        }
    }
}

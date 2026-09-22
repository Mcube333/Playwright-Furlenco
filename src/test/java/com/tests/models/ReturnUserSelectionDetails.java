package com.tests.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The {@code userSelectionDetails} block shared by POST /returns/request and POST
 * /returns/summary: the products being returned, the customer's stated reason(s), and (summary
 * only) the pickup {@code addressId}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReturnUserSelectionDetails {

    private List<ReturnItemRef> products;
    private List<String> enrichments;
    private Long addressId;

    private ReturnUserSelectionDetails(Builder builder) {
        this.products = builder.products;
        this.enrichments = builder.enrichments;
        this.addressId = builder.addressId;
    }

    public List<ReturnItemRef> getProducts() {
        return products;
    }

    public List<String> getEnrichments() {
        return enrichments;
    }

    public Long getAddressId() {
        return addressId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ReturnItemRef> products;
        private List<String> enrichments;
        private Long addressId;

        public Builder products(List<ReturnItemRef> products) {
            this.products = products;
            return this;
        }

        public Builder enrichments(List<String> enrichments) {
            this.enrichments = enrichments;
            return this;
        }

        public Builder addressId(Long addressId) {
            this.addressId = addressId;
            return this;
        }

        public ReturnUserSelectionDetails build() {
            return new ReturnUserSelectionDetails(this);
        }
    }
}

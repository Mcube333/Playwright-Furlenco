package com.tests.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * POST /bundles/breakage-dry-run body — checks whether returning/removing the given item(s) would
 * break a bundled discount, before the return is actually submitted. {@code actor} and
 * {@code actionType} were always the literal strings {@code "CUSTOMER"}/{@code "RETURN"} in every
 * captured request for the Returns flow.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BreakageDryRunRequest {

    private String actor;
    private List<Long> affectedItemIds;
    private List<Long> affectedCompositeItemIds;
    private String actionType;

    private BreakageDryRunRequest(Builder builder) {
        this.actor = builder.actor;
        this.affectedItemIds = builder.affectedItemIds;
        this.affectedCompositeItemIds = builder.affectedCompositeItemIds;
        this.actionType = builder.actionType;
    }

    public String getActor() {
        return actor;
    }

    public List<Long> getAffectedItemIds() {
        return affectedItemIds;
    }

    public List<Long> getAffectedCompositeItemIds() {
        return affectedCompositeItemIds;
    }

    public String getActionType() {
        return actionType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String actor = "CUSTOMER";
        private List<Long> affectedItemIds;
        private List<Long> affectedCompositeItemIds = List.of();
        private String actionType = "RETURN";

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder affectedItemIds(List<Long> affectedItemIds) {
            this.affectedItemIds = affectedItemIds;
            return this;
        }

        public Builder affectedCompositeItemIds(List<Long> affectedCompositeItemIds) {
            this.affectedCompositeItemIds = affectedCompositeItemIds;
            return this;
        }

        public Builder actionType(String actionType) {
            this.actionType = actionType;
            return this;
        }

        public BreakageDryRunRequest build() {
            return new BreakageDryRunRequest(this);
        }
    }
}

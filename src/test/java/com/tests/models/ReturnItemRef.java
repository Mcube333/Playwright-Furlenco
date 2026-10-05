package com.tests.models;

/**
 * A single product reference inside a Returns flow payload: {@code {"id":<itemId>,"type":"ITEM"}}.
 * {@code type} is always the literal string {@code "ITEM"} in every captured request/response —
 * no other value has been observed live.
 */
public class ReturnItemRef {

    private long id;
    private String type;

    private ReturnItemRef(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
    }

    public long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long id;
        private String type = "ITEM";

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public ReturnItemRef build() {
            return new ReturnItemRef(this);
        }
    }
}

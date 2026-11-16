package com.abrenoch.wledgrabber.common.util;

import java.util.Objects;

/** Structured field error shared by validator, repository and UI adapters. */
public final class FieldError {
    public enum Code {
        EMPTY,
        NOT_A_NUMBER,
        OUT_OF_RANGE,
        INVALID_VALUE,
        CROSS_FIELD,
        UNSUPPORTED_SCHEMA,
        CONFIG_MISSING,
        STORAGE,
        CAPABILITY_MISSING,
        STALE_OBSERVATION,
        IDENTITY_CHANGED,
        COUNT_MISMATCH,
        UNSUPPORTED_HARDWARE,
        CONFIRMATION_REQUIRED
    }

    private final String field;
    private final Code code;
    private final String detail;

    public FieldError(String field, Code code, String detail) {
        this.field = field;
        this.code = code;
        this.detail = detail;
    }

    public String field() {
        return field;
    }

    public Code code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldError)) {
            return false;
        }
        FieldError that = (FieldError) o;
        return Objects.equals(field, that.field)
                && code == that.code
                && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, code, detail);
    }

    @Override
    public String toString() {
        return field + ":" + code + (detail == null ? "" : ":" + detail);
    }
}

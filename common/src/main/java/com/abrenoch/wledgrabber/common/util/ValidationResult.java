package com.abrenoch.wledgrabber.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Value-or-errors result; invalid input never throws. */
public final class ValidationResult<T> {
    private final T value;
    private final List<FieldError> errors;

    private ValidationResult(T value, List<FieldError> errors) {
        this.value = value;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static <T> ValidationResult<T> ok(T value) {
        return new ValidationResult<>(value, Collections.emptyList());
    }

    public static <T> ValidationResult<T> fail(List<FieldError> errors) {
        return new ValidationResult<>(null, errors);
    }

    public static <T> ValidationResult<T> fail(FieldError error) {
        return new ValidationResult<>(null, Collections.singletonList(error));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public T value() {
        return value;
    }

    public List<FieldError> errors() {
        return errors;
    }

    public boolean hasError(String field, FieldError.Code code) {
        for (FieldError error : errors) {
            if (error.field().equals(field) && error.code() == code) {
                return true;
            }
        }
        return false;
    }

    public FieldError errorFor(String field) {
        for (FieldError error : errors) {
            if (error.field().equals(field)) {
                return error;
            }
        }
        return null;
    }
}

package com.codetrace.exception;

import com.codetrace.dto.ApiError;

import java.util.List;

/** Request content is invalid (HTTP 400). Carries per-field or per-file details for the UI. */
public class ApiValidationException extends RuntimeException {

    private final transient List<ApiError.FieldError> details;

    public ApiValidationException(String message) {
        this(message, List.of());
    }

    public ApiValidationException(String message, List<ApiError.FieldError> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<ApiError.FieldError> getDetails() {
        return details;
    }
}

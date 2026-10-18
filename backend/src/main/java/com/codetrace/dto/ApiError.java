package com.codetrace.dto;

import java.time.Instant;
import java.util.List;

/** Uniform error body for every non-2xx API response. Never contains stack traces or SQL. */
public record ApiError(Instant timestamp, int status, String error, String message, String path, List<FieldError> details) {

    public record FieldError(String field, String message) {
    }
}

package com.codetrace.exception;

/** An uploaded file exceeds the configured per-file limit (HTTP 413). */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}

package com.codetrace.exception;

import com.codetrace.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Converts every exception into the uniform {@link ApiError} JSON body with an appropriate status.
 * Internal details (stack traces, SQL, credentials) are logged server-side only, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiValidationException.class)
    ResponseEntity<ApiError> validation(ApiValidationException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, ex.getDetails());
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiError> bind(BindException ex, HttpServletRequest req) {
        List<ApiError.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(),
                        fe.isBindingFailure() ? "Invalid value" : fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ApiError.FieldError> details = ex.getAllErrors().stream()
                .map(e -> new ApiError.FieldError("request", e.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.FieldError> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", req, List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ApiError> missing(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "A required request parameter or file part is missing", req, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", req, List.of());
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, PayloadTooLargeException.class})
    ResponseEntity<ApiError> tooLarge(Exception ex, HttpServletRequest req) {
        String message = ex instanceof PayloadTooLargeException ? ex.getMessage()
                : "Upload exceeds the maximum allowed request or file size";
        return build(HttpStatus.PAYLOAD_TOO_LARGE, message, req, List.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> multipart(MultipartException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Expected a multipart/form-data upload", req, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint", req, List.of());
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception ex, HttpServletRequest req) {
        String message = ex instanceof ResourceNotFoundException ? ex.getMessage() : "Resource not found";
        return build(HttpStatus.NOT_FOUND, message, req, List.of());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ApiError> database(RuntimeException ex, HttpServletRequest req) {
        log.error("Database error on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getClass().getSimpleName(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "The database is unavailable or rejected the operation. Please try again later.", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req, List.of());
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req,
                                                  List<ApiError.FieldError> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}

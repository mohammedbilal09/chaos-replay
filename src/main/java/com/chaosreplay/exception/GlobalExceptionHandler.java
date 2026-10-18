package com.chaosreplay.exception;

import com.chaosreplay.metrics.ChaosReplayMetrics;
import com.chaosreplay.security.SecurityHeadersFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception translator producing standardized, secure, and explainable
 * {@link ApiErrorResponse} payloads across all REST controllers without leaking internal
 * stack traces, database schema constraints, or class hierarchies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired(required = false)
    private ChaosReplayMetrics metrics = new ChaosReplayMetrics(new SimpleMeterRegistry());

    public GlobalExceptionHandler() {
    }

    public GlobalExceptionHandler(ChaosReplayMetrics metrics) {
        this.metrics = metrics != null ? metrics : new ChaosReplayMetrics(new SimpleMeterRegistry());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        metrics.recordTelemetryInvalid("validation");
        String correlationId = resolveCorrelationId(request);

        List<ValidationErrorDetail> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(new ValidationErrorDetail(
                    fe.getField(),
                    fe.getDefaultMessage(),
                    sanitizeRejectedValue(fe.getRejectedValue())
            ));
        }

        String summary = details.stream()
                .map(d -> d.field() + ": " + d.message())
                .collect(Collectors.joining(", "));

        String message = summary.isBlank() ? "Validation failed" : summary;

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                message,
                request.getRequestURI(),
                "VALIDATION_FAILED",
                details,
                correlationId
        );

        log.warn("Validation error on request to [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        metrics.recordTelemetryInvalid("validation");
        String correlationId = resolveCorrelationId(request);

        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            String property = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "parameter";
            details.add(new ValidationErrorDetail(
                    property,
                    cv.getMessage(),
                    sanitizeRejectedValue(cv.getInvalidValue())
            ));
        }

        String summary = details.stream()
                .map(d -> d.field() + ": " + d.message())
                .collect(Collectors.joining(", "));

        String message = summary.isBlank() ? "Constraint violation" : summary;

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                message,
                request.getRequestURI(),
                "VALIDATION_FAILED",
                details,
                correlationId
        );

        log.warn("Constraint violation on request to [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        metrics.recordTelemetryInvalid("validation");
        String correlationId = resolveCorrelationId(request);

        List<ValidationErrorDetail> details = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> details.add(new ValidationErrorDetail(
                    paramName != null ? paramName : "parameter",
                    error.getDefaultMessage(),
                    null
            )));
        });

        String summary = details.stream()
                .map(d -> d.field() + ": " + d.message())
                .collect(Collectors.joining(", "));

        String message = summary.isBlank() ? "Method validation failed" : summary;

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                message,
                request.getRequestURI(),
                "VALIDATION_FAILED",
                details,
                correlationId
        );

        log.warn("Handler method validation error on request to [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                ex.getMessage(),
                request.getRequestURI(),
                "INVALID_ARGUMENT",
                null,
                correlationId
        );

        log.warn("Illegal argument error on request to [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameterException(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        String message = String.format("Required query parameter '%s' is missing", ex.getParameterName());

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                message,
                request.getRequestURI(),
                "MISSING_PARAMETER",
                null,
                correlationId
        );

        log.warn("Missing request parameter on [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.name(),
                ex.getMessage(),
                request.getRequestURI(),
                "RESOURCE_NOT_FOUND",
                null,
                correlationId
        );

        log.warn("Resource not found on [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.name(),
                "Resource not found: " + request.getRequestURI(),
                request.getRequestURI(),
                "RESOURCE_NOT_FOUND",
                null,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.name(),
                ex.getMessage(),
                request.getRequestURI(),
                "METHOD_NOT_ALLOWED",
                null,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.name(),
                ex.getMessage(),
                request.getRequestURI(),
                "UNSUPPORTED_MEDIA_TYPE",
                null,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_ACCEPTABLE.value(),
                HttpStatus.NOT_ACCEPTABLE.name(),
                "Requested media type cannot be served",
                request.getRequestURI(),
                "NOT_ACCEPTABLE",
                null,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(errorResponse);
    }

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEventException(
            DuplicateEventException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.name(),
                ex.getMessage(),
                request.getRequestURI(),
                "DUPLICATE_EVENT",
                null,
                correlationId
        );

        log.warn("Duplicate telemetry event detected on [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.name(),
                ex.getMessage(),
                request.getRequestURI(),
                "CONFLICT",
                null,
                correlationId
        );

        log.warn("Illegal state conflict on [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.name(),
                "Data integrity conflict: duplicate record or constraint violation",
                request.getRequestURI(),
                "DATA_INTEGRITY_VIOLATION",
                null,
                correlationId
        );

        log.warn("Data integrity violation on [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        metrics.recordTelemetryInvalid("malformed");
        String correlationId = resolveCorrelationId(request);

        String message = sanitizeUnreadableMessage(ex);

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                message,
                request.getRequestURI(),
                "MALFORMED_REQUEST",
                null,
                correlationId
        );

        log.warn("Unreadable HTTP message on [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        String paramName = ex.getName();
        String value = ex.getValue() != null ? sanitizeRejectedValue(ex.getValue()).toString() : "null";
        String message = String.format("Invalid value '%s' provided for parameter '%s'", value, paramName);

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                message,
                request.getRequestURI(),
                "TYPE_MISMATCH",
                null,
                correlationId
        );

        log.warn("Parameter type mismatch on [{}] (correlationId={}): {}",
                request.getRequestURI(), correlationId, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                "An unexpected internal error occurred",
                request.getRequestURI(),
                "INTERNAL_ERROR",
                null,
                correlationId
        );

        log.error("Unexpected error occurred while processing request to [{}] (correlationId={})",
                request.getRequestURI(), correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attr = request.getAttribute(SecurityHeadersFilter.CORRELATION_ID_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        String header = request.getHeader(SecurityHeadersFilter.CORRELATION_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return null;
    }

    private Object sanitizeRejectedValue(Object val) {
        if (val == null) {
            return null;
        }
        String str = val.toString();
        if (str.length() > 100) {
            return str.substring(0, 100) + "...";
        }
        return str;
    }

    private String sanitizeUnreadableMessage(HttpMessageNotReadableException ex) {
        String causeMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (causeMessage == null || causeMessage.isBlank()) {
            return "Malformed JSON or invalid request payload";
        }

        // Clean out internal Java package and class references
        String cleaned = causeMessage
                .replaceAll("com\\.chaosreplay\\.[a-zA-Z0-9_.]+", "domain model")
                .replaceAll("java\\.lang\\.[a-zA-Z0-9_.]+", "type")
                .replaceAll("java\\.time\\.[a-zA-Z0-9_.]+", "time");

        if (cleaned.length() > 250) {
            cleaned = cleaned.substring(0, 250) + "...";
        }
        return "Malformed JSON or invalid field value: " + cleaned;
    }
}

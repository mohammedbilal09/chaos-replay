package com.chaosreplay.exception;

/**
 * Encapsulates a single field-level validation violation.
 *
 * @param field         the name of the invalid property or parameter
 * @param message       human-readable validation constraint failure message
 * @param rejectedValue safe representation of the rejected value
 */
public record ValidationErrorDetail(
        String field,
        String message,
        Object rejectedValue
) {
}


package com.chaosreplay.exception;

/**
 * Thrown when a requested resource (such as a traceId or requestId) is not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}


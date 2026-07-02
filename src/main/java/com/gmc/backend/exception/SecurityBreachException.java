package com.gmc.backend.exception;

public class SecurityBreachException extends RuntimeException {

    public SecurityBreachException() {
        super("Refresh token reuse detected. All sessions have been invalidated.");
    }
}

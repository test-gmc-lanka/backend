package com.gmc.backend.exception;

public class InvalidTokenException extends RuntimeException {

    private final String errorCode;

    public InvalidTokenException(String errorCode, String humanMessage) {
        super(humanMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

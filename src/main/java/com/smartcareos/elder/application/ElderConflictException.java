package com.smartcareos.elder.application;

public class ElderConflictException extends RuntimeException {
    public ElderConflictException(String message) {
        super(message);
    }

    public ElderConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}


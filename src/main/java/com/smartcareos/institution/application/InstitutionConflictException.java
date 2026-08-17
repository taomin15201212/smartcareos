package com.smartcareos.institution.application;

public class InstitutionConflictException extends RuntimeException {
    public InstitutionConflictException(String message) {
        super(message);
    }

    public InstitutionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}


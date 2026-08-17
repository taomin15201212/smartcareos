package com.smartcareos.institution.application;

public class InstitutionNotFoundException extends RuntimeException {
    public InstitutionNotFoundException(String resource, String id) {
        super(resource + " not found: " + id);
    }
}


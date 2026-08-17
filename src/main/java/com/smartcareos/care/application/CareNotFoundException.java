package com.smartcareos.care.application;

public class CareNotFoundException extends RuntimeException {
    public CareNotFoundException(String resource, String id) {
        super(resource + " not found: " + id);
    }
}


package com.smartcareos.elder.application;

public class ElderNotFoundException extends RuntimeException {
    public ElderNotFoundException(String resource, String id) {
        super(resource + " not found: " + id);
    }
}


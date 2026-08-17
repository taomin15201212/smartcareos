package com.smartcareos.device.application;

public class DeviceConflictException extends RuntimeException {
    public DeviceConflictException(String message) {
        super(message);
    }

    public DeviceConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}


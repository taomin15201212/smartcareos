package com.smartcareos.device.application;

public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException(String deviceId) {
        super("device not found: " + deviceId);
    }
}


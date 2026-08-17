package com.smartcareos.alarm.application;

public final class AlarmNotFoundException extends RuntimeException {

    public AlarmNotFoundException(String alarmId) {
        super("alarm not found: " + alarmId);
    }
}

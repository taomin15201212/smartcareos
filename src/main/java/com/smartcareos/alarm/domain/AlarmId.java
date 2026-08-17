package com.smartcareos.alarm.domain;

import java.util.Objects;
import java.util.UUID;

public record AlarmId(UUID value) {

    public AlarmId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static AlarmId newId() {
        return new AlarmId(UUID.randomUUID());
    }

    public static AlarmId parse(String value) {
        return new AlarmId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

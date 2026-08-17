package com.smartcareos.messaging.application;

public class InboxPayloadConflictException extends RuntimeException {

    public InboxPayloadConflictException(String consumerName, String tenantId, String eventId) {
        super("event payload differs from the previously processed event: "
                + consumerName + "/" + tenantId + "/" + eventId);
    }
}

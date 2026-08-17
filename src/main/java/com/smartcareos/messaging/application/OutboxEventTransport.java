package com.smartcareos.messaging.application;

import com.smartcareos.messaging.domain.OutboxMessage;

@FunctionalInterface
public interface OutboxEventTransport {

    void publish(OutboxMessage message) throws Exception;
}

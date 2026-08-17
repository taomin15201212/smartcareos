package com.smartcareos.messaging.infrastructure;

import com.smartcareos.messaging.application.OutboxEventTransport;
import com.smartcareos.messaging.domain.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("outbox-log")
public class LoggingOutboxEventTransport implements OutboxEventTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOutboxEventTransport.class);

    @Override
    public void publish(OutboxMessage message) {
        LOGGER.info(
                "Outbox development delivery eventId={} tenantId={} aggregateType={} aggregateId={} eventType={} attempt={}",
                message.eventId(),
                message.tenantId(),
                message.aggregateType(),
                message.aggregateId(),
                message.eventType(),
                message.attemptCount());
    }
}

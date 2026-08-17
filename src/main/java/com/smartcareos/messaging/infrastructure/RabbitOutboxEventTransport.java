package com.smartcareos.messaging.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.messaging.application.OutboxEventTransport;
import com.smartcareos.messaging.domain.OutboxMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Profile("rabbitmq")
public class RabbitOutboxEventTransport implements OutboxEventTransport {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    public RabbitOutboxEventTransport(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
            @Value("${smartcareos.rabbitmq.confirm-timeout:PT5S}") Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(OutboxMessage outbox) throws Exception {
        JsonNode businessPayload = objectMapper.readTree(outbox.payload());
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "eventId", outbox.eventId(),
                "tenantId", outbox.tenantId(),
                "aggregateType", outbox.aggregateType(),
                "aggregateId", outbox.aggregateId(),
                "eventType", outbox.eventType(),
                "schemaVersion", outbox.schemaVersion(),
                "occurredAt", outbox.occurredAt().toString(),
                "payload", businessPayload));
        Message message = MessageBuilder.withBody(body)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(outbox.eventId())
                .setHeader("tenantId", outbox.tenantId())
                .setHeader("eventType", outbox.eventType())
                .build();
        CorrelationData correlation = new CorrelationData(outbox.eventId());
        rabbitTemplate.send(RabbitOutboxConfiguration.EXCHANGE, outbox.eventType(),
                message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ rejected event " + outbox.eventId()
                    + ": " + confirm.getReason());
        }
    }
}

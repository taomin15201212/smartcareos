package com.smartcareos.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.messaging.domain.OutboxMessage;
import com.smartcareos.messaging.infrastructure.RabbitOutboxConfiguration;
import com.smartcareos.messaging.infrastructure.RabbitOutboxEventTransport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitOutboxEventTransportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishesJsonEnvelopeAndWaitsForBrokerAck() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq(RabbitOutboxConfiguration.EXCHANGE), eq("AlarmCreated.v1"),
                any(Message.class), any(CorrelationData.class));
        RabbitOutboxEventTransport transport = new RabbitOutboxEventTransport(
                rabbitTemplate, objectMapper, Duration.ofSeconds(1));

        transport.publish(message());

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(RabbitOutboxConfiguration.EXCHANGE),
                eq("AlarmCreated.v1"), sent.capture(), any(CorrelationData.class));
        JsonNode envelope = objectMapper.readTree(sent.getValue().getBody());
        assertThat(envelope.path("eventId").asText()).isEqualTo("event-1");
        assertThat(envelope.path("tenantId").asText()).isEqualTo("tenant-1");
        assertThat(envelope.path("payload").path("status").asText()).isEqualTo("NEW");
        assertThat(sent.getValue().getMessageProperties().getMessageId()).isEqualTo("event-1");
        assertThat(sent.getValue().getMessageProperties().getHeaders())
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("eventType", "AlarmCreated.v1");
    }

    @Test
    void rejectsBrokerNackSoOutboxCanRetry() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "exchange unavailable"));
            return null;
        }).when(rabbitTemplate).send(any(String.class), any(String.class),
                any(Message.class), any(CorrelationData.class));
        RabbitOutboxEventTransport transport = new RabbitOutboxEventTransport(
                rabbitTemplate, objectMapper, Duration.ofSeconds(1));

        assertThatThrownBy(() -> transport.publish(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exchange unavailable");
    }

    private OutboxMessage message() {
        return new OutboxMessage(
                "event-1", "tenant-1", "Alarm", "alarm-1", "AlarmCreated.v1", 1,
                Instant.parse("2026-08-17T01:00:00Z"), "{\"status\":\"NEW\"}", 1);
    }
}

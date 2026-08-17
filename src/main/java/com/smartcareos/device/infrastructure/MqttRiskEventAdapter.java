package com.smartcareos.device.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.device.application.DeviceRiskEventHandler;
import com.smartcareos.device.domain.RiskSourceEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Broker-neutral MQTT ingress core. A transport adapter calls accept(topic,payload). */
@Component
public class MqttRiskEventAdapter {
    private static final Pattern TOPIC = Pattern.compile("^smartcare/([^/]+)/devices/([^/]+)/risk-events$");
    private final ObjectMapper mapper;
    private final DeviceRiskEventHandler handler;
    public MqttRiskEventAdapter(ObjectMapper mapper, DeviceRiskEventHandler handler) {
        this.mapper = mapper; this.handler = handler;
    }
    public DeviceRiskEventHandler.HandleResult accept(String topic, byte[] payload) {
        Matcher match = TOPIC.matcher(topic);
        if (!match.matches()) throw new IllegalArgumentException("unsupported MQTT topic");
        try {
            Payload body = mapper.readValue(payload, Payload.class);
            if (!match.group(1).equals(body.tenantId()) || !match.group(2).equals(body.deviceId()))
                throw new IllegalArgumentException("MQTT topic and payload identity mismatch");
            return handler.handle(new RiskSourceEvent(body.eventId(), body.tenantId(), body.deviceId(),
                    body.elderId(), body.severity(), body.observedAt()));
        } catch (IllegalArgumentException exception) { throw exception;
        } catch (Exception exception) { throw new IllegalArgumentException("invalid MQTT risk payload", exception); }
    }
    public record Payload(String eventId, String tenantId, String deviceId, String elderId,
                          AlarmSeverity severity, Instant observedAt) {}
}

package com.smartcareos.device.application;

import com.smartcareos.alarm.application.AlarmApplicationService;
import com.smartcareos.alarm.application.AlarmSnapshot;
import com.smartcareos.device.domain.RiskSourceEvent;
import com.smartcareos.device.domain.RiskSourceEventRepository;
import com.smartcareos.messaging.application.InboxStore;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DeviceRiskEventHandler {

    static final String CONSUMER_NAME = "device-risk-to-alarm-v1";

    private final InboxStore inboxStore;
    private final RiskSourceEventRepository eventRepository;
    private final AlarmApplicationService alarmService;
    private final DeviceRegistryService deviceRegistryService;
    private final Clock clock;

    public DeviceRiskEventHandler(
            InboxStore inboxStore,
            RiskSourceEventRepository eventRepository,
            AlarmApplicationService alarmService,
            DeviceRegistryService deviceRegistryService,
            Clock clock
    ) {
        this.inboxStore = inboxStore;
        this.eventRepository = eventRepository;
        this.alarmService = alarmService;
        this.deviceRegistryService = deviceRegistryService;
        this.clock = clock;
    }

    public HandleResult handle(RiskSourceEvent event) {
        Instant receivedAt = clock.instant();
        AtomicReference<AlarmSnapshot> alarm = new AtomicReference<>();
        boolean processed = inboxStore.processOnce(
                CONSUMER_NAME,
                event.tenantId(),
                event.eventId(),
                fingerprint(event),
                receivedAt,
                () -> {
                    deviceRegistryService.validateRiskEvent(
                            event.tenantId(), event.deviceId(), event.elderId(), event.observedAt());
                    eventRepository.save(event, receivedAt);
                    alarm.set(alarmService.create(new AlarmApplicationService.CreateCommand(
                            event.tenantId(),
                            event.elderId(),
                            event.eventId(),
                            event.severity())).alarm());
                });

        if (!processed) {
            alarm.set(alarmService.findBySourceEvent(event.tenantId(), event.eventId())
                    .orElseThrow(() -> new IllegalStateException(
                            "processed risk source event has no alarm: " + event.eventId())));
        }
        return new HandleResult(processed ? Outcome.PROCESSED : Outcome.DUPLICATE, alarm.get());
    }

    static String fingerprint(RiskSourceEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            addField(digest, event.eventId());
            addField(digest, event.tenantId());
            addField(digest, event.deviceId());
            addField(digest, event.elderId());
            addField(digest, event.severity().name());
            addField(digest, event.observedAt().toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void addField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public enum Outcome {
        PROCESSED,
        DUPLICATE
    }

    public record HandleResult(Outcome outcome, AlarmSnapshot alarm) {
    }
}

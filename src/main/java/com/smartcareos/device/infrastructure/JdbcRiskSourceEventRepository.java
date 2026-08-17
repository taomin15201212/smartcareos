package com.smartcareos.device.infrastructure;

import com.smartcareos.device.domain.RiskSourceEvent;
import com.smartcareos.device.domain.RiskSourceEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class JdbcRiskSourceEventRepository implements RiskSourceEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRiskSourceEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(RiskSourceEvent event, Instant receivedAt) {
        jdbcTemplate.update("""
                        INSERT INTO device_risk_event (
                            tenant_id, event_id, device_id, elder_id, severity,
                            observed_at, received_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                event.tenantId(),
                event.eventId(),
                event.deviceId(),
                event.elderId(),
                event.severity().name(),
                Timestamp.from(event.observedAt()),
                Timestamp.from(receivedAt));
    }
}

package com.smartcareos.device.domain;

import java.time.Instant;

public interface RiskSourceEventRepository {

    void save(RiskSourceEvent event, Instant receivedAt);
}

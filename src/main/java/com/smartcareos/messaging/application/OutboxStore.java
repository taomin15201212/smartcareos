package com.smartcareos.messaging.application;

import com.smartcareos.messaging.domain.OutboxMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface OutboxStore {

    List<OutboxMessage> claimBatch(String workerId, int batchSize, Instant now, Duration lease);

    void markPublished(String eventId, String workerId, Instant publishedAt);

    void markFailed(String eventId, String workerId, String error, Instant nextAttemptAt);
}

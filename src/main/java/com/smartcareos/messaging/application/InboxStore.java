package com.smartcareos.messaging.application;

import java.time.Instant;

public interface InboxStore {

    boolean processOnce(
            String consumerName,
            String tenantId,
            String eventId,
            String payloadHash,
            Instant receivedAt,
            Runnable processing
    );
}

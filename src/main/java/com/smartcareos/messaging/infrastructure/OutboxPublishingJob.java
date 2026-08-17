package com.smartcareos.messaging.infrastructure;

import com.smartcareos.messaging.application.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class OutboxPublishingJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublishingJob.class);

    private final OutboxPublisher publisher;

    public OutboxPublishingJob(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${smartcareos.outbox.fixed-delay:PT1S}")
    public void publishDueEvents() {
        OutboxPublisher.PublishBatchResult result = publisher.publishBatch();
        if (result.claimed() > 0) {
            LOGGER.info(
                    "Outbox batch complete claimed={} published={} failed={}",
                    result.claimed(), result.published(), result.failed());
        }
    }
}

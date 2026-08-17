package com.smartcareos.messaging.infrastructure;

import com.smartcareos.messaging.application.OutboxEventTransport;
import com.smartcareos.messaging.application.OutboxPublisher;
import com.smartcareos.messaging.application.OutboxStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration
@EnableScheduling
@ConditionalOnBean(OutboxEventTransport.class)
@ConditionalOnProperty(prefix = "smartcareos.outbox", name = "enabled", havingValue = "true")
public class OutboxSchedulingConfiguration {

    @Bean
    OutboxPublisher outboxPublisher(
            OutboxStore store,
            OutboxEventTransport transport,
            Clock clock,
            @Value("${smartcareos.outbox.batch-size:100}") int batchSize,
            @Value("${smartcareos.outbox.lease:PT1M}") Duration lease,
            @Value("${smartcareos.outbox.base-backoff:PT2S}") Duration baseBackoff,
            @Value("${smartcareos.outbox.max-backoff:PT15M}") Duration maxBackoff
    ) {
        return new OutboxPublisher(
                store,
                transport,
                clock,
                "smartcareos-" + UUID.randomUUID(),
                batchSize,
                lease,
                baseBackoff,
                maxBackoff);
    }

    @Bean
    OutboxPublishingJob outboxPublishingJob(OutboxPublisher publisher) {
        return new OutboxPublishingJob(publisher);
    }
}

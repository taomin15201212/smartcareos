package com.smartcareos.messaging.application;

import com.smartcareos.messaging.domain.OutboxMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class OutboxPublisher {

    private final OutboxStore store;
    private final OutboxEventTransport transport;
    private final Clock clock;
    private final String workerId;
    private final int batchSize;
    private final Duration lease;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public OutboxPublisher(
            OutboxStore store,
            OutboxEventTransport transport,
            Clock clock,
            String workerId,
            int batchSize,
            Duration lease,
            Duration baseBackoff,
            Duration maxBackoff
    ) {
        this.store = Objects.requireNonNull(store);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
        this.workerId = requireText(workerId, "workerId");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.lease = requirePositive(lease, "lease");
        this.baseBackoff = requirePositive(baseBackoff, "baseBackoff");
        this.maxBackoff = requirePositive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must not be shorter than baseBackoff");
        }
    }

    public PublishBatchResult publishBatch() {
        Instant now = clock.instant();
        List<OutboxMessage> messages = store.claimBatch(workerId, batchSize, now, lease);
        int published = 0;
        int failed = 0;

        for (OutboxMessage message : messages) {
            try {
                transport.publish(message);
                store.markPublished(message.eventId(), workerId, clock.instant());
                published++;
            } catch (Exception exception) {
                store.markFailed(
                        message.eventId(),
                        workerId,
                        safeError(exception),
                        clock.instant().plus(backoffFor(message.attemptCount())));
                failed++;
            }
        }
        return new PublishBatchResult(messages.size(), published, failed);
    }

    private Duration backoffFor(int attemptCount) {
        long multiplier = 1L << Math.min(attemptCount - 1, 20);
        Duration candidate;
        try {
            candidate = baseBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return maxBackoff;
        }
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }

    private static String safeError(Exception exception) {
        String message = exception.getMessage();
        String value = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public record PublishBatchResult(int claimed, int published, int failed) {
    }
}

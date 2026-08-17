package com.smartcareos.messaging.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitOutboxConfigurationTest {

    private final RabbitOutboxConfiguration.IntegrationTopology topology =
            new RabbitOutboxConfiguration.IntegrationTopology();

    @Test
    void integrationQueueIsDurableAndRoutesRejectedMessagesToDurableDlq() {
        Queue main = topology.smartCareIntegrationQueue();
        DirectExchange dlx = topology.smartCareDeadLetterExchange();
        Queue dlq = topology.smartCareIntegrationDeadLetterQueue();
        Binding binding = topology.smartCareIntegrationDeadLetterBinding(dlq, dlx);

        assertThat(main.isDurable()).isTrue();
        assertThat(main.getArguments())
                .containsEntry("x-dead-letter-exchange",
                        RabbitOutboxConfiguration.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key",
                        RabbitOutboxConfiguration.INTEGRATION_DEAD_LETTER_ROUTING_KEY);
        assertThat(dlx.isDurable()).isTrue();
        assertThat(dlq.isDurable()).isTrue();
        assertThat(binding.getRoutingKey())
                .isEqualTo(RabbitOutboxConfiguration.INTEGRATION_DEAD_LETTER_ROUTING_KEY);
    }
}

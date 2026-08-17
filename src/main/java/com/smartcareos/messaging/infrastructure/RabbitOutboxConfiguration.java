package com.smartcareos.messaging.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("rabbitmq")
public class RabbitOutboxConfiguration {
    public static final String EXCHANGE = "smartcare.events";
    public static final String INTEGRATION_QUEUE = "smartcare.events.integration.v2";
    public static final String DEAD_LETTER_EXCHANGE = "smartcare.events.dlx";
    public static final String INTEGRATION_DEAD_LETTER_QUEUE =
            "smartcare.events.integration.v2.dlq";
    public static final String INTEGRATION_DEAD_LETTER_ROUTING_KEY =
            "smartcare.events.integration.v2.dead";

    @Bean
    TopicExchange smartCareEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Configuration
    @Profile("integration")
    static class IntegrationTopology {
        @Bean
        Queue smartCareIntegrationQueue() {
            return QueueBuilder.durable(INTEGRATION_QUEUE)
                    .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(INTEGRATION_DEAD_LETTER_ROUTING_KEY)
                    .build();
        }

        @Bean
        DirectExchange smartCareDeadLetterExchange() {
            return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        }

        @Bean
        Queue smartCareIntegrationDeadLetterQueue() {
            return QueueBuilder.durable(INTEGRATION_DEAD_LETTER_QUEUE).build();
        }

        @Bean
        Binding smartCareIntegrationBinding(Queue smartCareIntegrationQueue,
                TopicExchange smartCareEventsExchange) {
            return BindingBuilder.bind(smartCareIntegrationQueue)
                    .to(smartCareEventsExchange).with("#");
        }

        @Bean
        Binding smartCareIntegrationDeadLetterBinding(
                Queue smartCareIntegrationDeadLetterQueue,
                DirectExchange smartCareDeadLetterExchange
        ) {
            return BindingBuilder.bind(smartCareIntegrationDeadLetterQueue)
                    .to(smartCareDeadLetterExchange)
                    .with(INTEGRATION_DEAD_LETTER_ROUTING_KEY);
        }
    }
}

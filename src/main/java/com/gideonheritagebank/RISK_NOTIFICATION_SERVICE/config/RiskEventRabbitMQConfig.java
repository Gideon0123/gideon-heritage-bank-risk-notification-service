package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskEventRabbitMQConfig {

    @Bean
    public TopicExchange riskEventsExchange(
            @Value("${risk.messaging.exchange:bank.risk-events.exchange}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange riskEventsDeadLetterExchange(
            @Value("${risk.messaging.dead-letter-exchange:bank.risk-events.dlx}") String exchangeName
    ) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue riskAssessmentEventsQueue(
            @Value("${risk.messaging.queue:risk.assessment.events}") String queueName,
            @Value("${risk.messaging.dead-letter-exchange:bank.risk-events.dlx}") String deadLetterExchange,
            @Value("${risk.messaging.dead-letter-routing-key:risk.assessment.dead}") String deadLetterRoutingKey
    ) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue riskAssessmentDeadLetterQueue(
            @Value("${risk.messaging.dead-letter-queue:risk.assessment.events.dlq}") String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding riskAssessmentEventsBinding(
            @Qualifier("riskAssessmentEventsQueue") Queue queue,
            @Qualifier("riskEventsExchange") TopicExchange exchange,
            @Value("${risk.messaging.routing-key:risk.#}") String routingKey
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    @Bean
    public Binding riskAssessmentDeadLetterBinding(
            @Qualifier("riskAssessmentDeadLetterQueue") Queue queue,
            @Qualifier("riskEventsDeadLetterExchange") DirectExchange exchange,
            @Value("${risk.messaging.dead-letter-routing-key:risk.assessment.dead}") String routingKey
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}

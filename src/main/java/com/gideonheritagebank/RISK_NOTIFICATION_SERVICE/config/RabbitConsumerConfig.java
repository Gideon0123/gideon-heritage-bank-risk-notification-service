package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableRabbit
public class RabbitConsumerConfig {

    @Bean
    public Advice identityEventRetryInterceptor() {

        return RetryInterceptorBuilder.stateless()

                .configureRetryPolicy(policy -> policy

                        /*
                         * Four retries after the initial attempt.
                         * Total maximum executions = 5.
                         */
                        .maxRetries(4)

                        /*
                         * Initial retry delay: 1 second.
                         */
                        .delay(Duration.ofSeconds(1))

                        /*
                         * Exponential backoff:
                         * 1s -> 2s -> 4s -> 8s
                         */
                        .multiplier(2.0)

                        /*
                         * Maximum retry delay.
                         */
                        .maxDelay(Duration.ofSeconds(10))

                        /*
                         * Invalid messages must not be retried.
                         */
                        .excludes(
                                AmqpRejectAndDontRequeueException.class
                        )
                )

                .recoverer(new RejectAndDontRequeueRecoverer())

                .build();
    }

    @Bean(name = "identityEventListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory identityEventListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Advice identityEventRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);

        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        factory.setPrefetchCount(20);

        factory.setConcurrentConsumers(2);

        factory.setMaxConcurrentConsumers(8);

        factory.setDefaultRequeueRejected(false);

        factory.setAdviceChain(identityEventRetryInterceptor);

        return factory;
    }
}

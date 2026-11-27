package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RiskEventConsumerConfiguration {

    @Bean
    public Advice riskEventRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .configureRetryPolicy(policy -> policy
                        .maxRetries(4)
                        .delay(Duration.ofSeconds(1))
                        .multiplier(2.0)
                        .maxDelay(Duration.ofSeconds(10))
                        .excludes(AmqpRejectAndDontRequeueException.class)
                )
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean(name = "riskEventListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory riskEventListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("riskEventRetryInterceptor") Advice retryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(6);
        factory.setPrefetchCount(20);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}

package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationEmailProperties.class)
public class NotificationEmailConfiguration {

    @Bean(name = "notificationDeliveryListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory notificationDeliveryListenerContainerFactory(
            ConnectionFactory connectionFactory
    ) {

        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);

        /*
         * The email consumer explicitly acknowledges messages
         * after the delivery request has been handled.
         */
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(6);

        /*
         * The consumer handles permanent and temporary failures
         * explicitly. Unexpected infrastructure failures are
         * requeued by the listener itself.
         */
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}
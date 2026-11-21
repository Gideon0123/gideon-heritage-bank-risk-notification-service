package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationDeliveryRabbitMQConfig {

    @Bean
    public DirectExchange notificationDeliveryExchange(
            NotificationOutboxProperties properties
    ) {

        return new DirectExchange(
                properties.getExchange(),
                true,
                false
        );
    }

    // EMAIL DELIVERY QUEUE
    @Bean
    public Queue notificationEmailDeliveryQueue() {

        return QueueBuilder
                .durable("notification.delivery.email")
                .build();
    }

    @Bean
    public Binding notificationEmailDeliveryBinding(
            Queue notificationEmailDeliveryQueue,
            @Qualifier("notificationDeliveryExchange")
            DirectExchange notificationDeliveryExchange,
            NotificationOutboxProperties properties
    ) {
        return BindingBuilder
                .bind(notificationEmailDeliveryQueue)
                .to(notificationDeliveryExchange)
                .with(properties.getEmailRoutingKey());
    }


    // PUSH DELIVERY QUEUE
    @Bean
    public Queue notificationPushDeliveryQueue() {

        return QueueBuilder
                .durable("notification.delivery.push")
                .build();
    }

    @Bean
    public Binding notificationPushDeliveryBinding(
            Queue notificationPushDeliveryQueue,
            @Qualifier("notificationDeliveryExchange")
            DirectExchange notificationDeliveryExchange,
            NotificationOutboxProperties properties
    ) {

        return BindingBuilder
                .bind(notificationPushDeliveryQueue)
                .to(notificationDeliveryExchange)
                .with(properties.getPushRoutingKey());
    }


    // IN-APP DELIVERY QUEUE
    @Bean
    public Queue notificationInAppDeliveryQueue() {

        return QueueBuilder
                .durable("notification.delivery.in-app")
                .build();
    }

    @Bean
    public Binding notificationInAppDeliveryBinding(
            Queue notificationInAppDeliveryQueue,
            @Qualifier("notificationDeliveryExchange")
            DirectExchange notificationDeliveryExchange,
            NotificationOutboxProperties properties
    ) {

        return BindingBuilder
                .bind(notificationInAppDeliveryQueue)
                .to(notificationDeliveryExchange)
                .with(properties.getInAppRoutingKey());
    }
}

package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    /* The exchange must match the exchange declared by Identity Service. */
    @Bean
    public DirectExchange identityNotificationExchange(
            @Value("${notification.messaging.exchange:bank.notifications.exchange}")
            String exchangeName
    ) {

        return new DirectExchange(
                exchangeName,
                true,
                false
        );
    }

    /* CUSTOMER EMAIL VERIFICATION */
    @Bean
    public Queue emailVerificationQueue(
            @Value("${notification.messaging.email-verification-queue:identity.email-verification}")
            String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding emailVerificationBinding(
            Queue emailVerificationQueue,
            DirectExchange identityNotificationExchange,
            @Value("${notification.messaging.email-verification-routing-key:notification.email-verification}")
            String routingKey
    ) {
        return BindingBuilder
                .bind(emailVerificationQueue)
                .to(identityNotificationExchange)
                .with(routingKey);
    }

    /* PASSWORD RESET */
    @Bean
    public Queue passwordResetQueue(
            @Value("${notification.messaging.password-reset-queue:identity.password-reset}")
            String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding passwordResetBinding(
            Queue passwordResetQueue,
            DirectExchange identityNotificationExchange,
            @Value("${notification.messaging.password-reset-routing-key:notification.password-reset}")
            String routingKey
    ) {
        return BindingBuilder
                .bind(passwordResetQueue)
                .to(identityNotificationExchange)
                .with(routingKey);
    }

    /* STAFF ONBOARDING */
    @Bean
    public Queue staffOnboardingQueue(
            @Value("${notification.messaging.staff-onboarding-queue:identity.staff-onboarding}")
            String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding staffOnboardingBinding(
            Queue staffOnboardingQueue,
            DirectExchange identityNotificationExchange,
            @Value("${notification.messaging.staff-onboarding-routing-key:notification.staff-onboarding}")
            String routingKey
    ) {
        return BindingBuilder
                .bind(staffOnboardingQueue)
                .to(identityNotificationExchange)
                .with(routingKey);
    }

    /* CUSTOMER CONVERTED TO STAFF */
    @Bean
    public Queue customerConvertedToStaffQueue(
            @Value("${notification.messaging.customer-converted-queue:identity.customer-converted-to-staff}")
            String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding customerConvertedToStaffBinding(
            Queue customerConvertedToStaffQueue,
            DirectExchange identityNotificationExchange,
            @Value("${notification.messaging.customer-converted-routing-key:notification.customer-converted-to-staff}")
            String routingKey
    ) {
        return BindingBuilder
                .bind(customerConvertedToStaffQueue)
                .to(identityNotificationExchange)
                .with(routingKey);
    }

    /* EMAIL CHANGE VERIFICATION */
    @Bean
    public Queue emailChangeVerificationQueue(
            @Value("${notification.messaging.email-change-verification-queue:identity.email-change-verification}")
            String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding emailChangeVerificationBinding(
            Queue emailChangeVerificationQueue,
            DirectExchange identityNotificationExchange,
            @Value("${notification.messaging.email-change-verification-routing-key:notification.email-change-verification}")
            String routingKey
    ) {
        return BindingBuilder
                .bind(emailChangeVerificationQueue)
                .to(identityNotificationExchange)
                .with(routingKey);
    }

    /* EMAIL CHANGE CONFIRMATION */
    @Bean
    public Queue emailChangedQueue(
            @Value("${notification.messaging.email-changed-queue:identity.email-changed}")
            String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding emailChangedBinding(
            Queue emailChangedQueue,
            DirectExchange identityNotificationExchange,
            @Value("${notification.messaging.email-changed-routing-key:notification.email-changed}")
            String routingKey
    ) {
        return BindingBuilder
                .bind(emailChangedQueue)
                .to(identityNotificationExchange)
                .with(routingKey);
    }

    /* RABBIT ADMIN */
    @Bean
    public RabbitAdmin rabbitAdmin(
            ConnectionFactory connectionFactory
    ) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public ApplicationRunner declareRabbitResources(
            AmqpAdmin amqpAdmin
    ) {
        return args -> amqpAdmin.initialize();
    }
}
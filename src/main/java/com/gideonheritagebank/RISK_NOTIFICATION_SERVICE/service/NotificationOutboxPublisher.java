package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.NotificationOutboxPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxPublisher {

    private final RabbitTemplate rabbitTemplate;

    private final NotificationOutboxProperties outboxProperties;


    public void publish(NotificationOutbox outbox) {

        if (outbox == null) {
            throw new IllegalArgumentException(
                    "Outbox record is required."
            );
        }

        CorrelationData correlationData =
                new CorrelationData(outbox.getMessageId().toString());

        Message message = MessageBuilder
                .withBody(
                        outbox.getPayload().getBytes(StandardCharsets.UTF_8)
                )
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(outbox.getMessageId().toString())
                .setHeader("eventType", outbox.getEventType())
                .setHeader("deliveryId", outbox.getDeliveryId().toString())
                .build();

        try {

            rabbitTemplate.send(
                    outbox.getExchangeName(),
                    outbox.getRoutingKey(),
                    message,
                    correlationData
            );

            CorrelationData.Confirm confirm =
                    correlationData.getFuture().get(
                            outboxProperties.getPublisherConfirmTimeoutSeconds(),
                            TimeUnit.SECONDS
                    );

            if (!confirm.ack()) {

                throw new NotificationOutboxPublishException(
                        "RabbitMQ negatively acknowledged message. "
                                + confirm.reason()
                );
            }

            if (correlationData.getReturned() != null) {

                throw new NotificationOutboxPublishException(
                        "RabbitMQ returned an unroutable message. "
                                + correlationData.getReturned().getReplyText()
                );
            }

            log.info(
                    "Outbox message published. outboxId={}, messageId={}, deliveryId={}",
                    outbox.getId(),
                    outbox.getMessageId(),
                    outbox.getDeliveryId()
            );

        } catch (TimeoutException exception) {

            throw new NotificationOutboxPublishException(
                    "Timed out waiting for RabbitMQ publisher confirmation.",
                    exception
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new NotificationOutboxPublishException(
                    "Interrupted while waiting for RabbitMQ publisher confirmation.",
                    exception
            );

        } catch (NotificationOutboxPublishException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new NotificationOutboxPublishException(
                    "Unable to publish notification outbox message.",
                    exception
            );
        }
    }
}

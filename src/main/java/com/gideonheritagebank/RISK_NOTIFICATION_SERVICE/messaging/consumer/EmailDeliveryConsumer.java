package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.event.NotificationDeliveryRequestedEvent;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.InvalidEmailDeliveryMessageException;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service.EmailDeliveryExecutionService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryConsumer {

    private static final String EXPECTED_EVENT_TYPE = "NOTIFICATION_DELIVERY_REQUESTED";
    private final ObjectMapper objectMapper;
    private final EmailDeliveryExecutionService executionService;

    /*
     * One worker ID per running application instance.
     * The database lease prevents another worker from processing
     * a delivery while this worker owns it.
     */
    private final String workerId = "email-worker-" + UUID.randomUUID();

    @RabbitListener(
            queues = "${notification.email.queue:notification.delivery.email}",
            containerFactory = "notificationDeliveryListenerContainerFactory"
    )
    public void consume(
            Message message,
            Channel channel
    ) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {

            validateMessageContentType(message);

            NotificationDeliveryRequestedEvent event = objectMapper.readValue(
                    message.getBody(),
                    NotificationDeliveryRequestedEvent.class
            );

            validateEvent(event);

            executionService.process(
                    event.deliveryId(),
                    event.notificationId(),
                    workerId
            );

            /*
             * The execution service returns normally after:
             *
             * - successful SMTP submission and SENT persistence;
             * - a retry has been persisted;
             * - a permanent failure has been persisted; or
             * - a duplicate/already-processed delivery was skipped.
             *
             * Therefore, the RabbitMQ trigger can be acknowledged.
             */
            channel.basicAck(
                    deliveryTag,
                    false
            );

            log.debug(
                    "Email delivery request acknowledged. deliveryId={}",
                    event.deliveryId()
            );

        } catch (InvalidEmailDeliveryMessageException
                 | com.fasterxml.jackson.core.JsonProcessingException exception) {

            /*
             * Malformed messages and invalid event contracts
             * should not be requeued indefinitely.
             */
            log.error(
                    "Rejecting invalid email delivery message. reason={}",
                    exception.getMessage()
            );

            channel.basicReject(
                    deliveryTag,
                    false
            );

        } catch (Exception exception) {

            /*
             * Database outages, connection failures and unexpected
             * infrastructure failures should be retried by RabbitMQ.
             *
             * If the delivery was already claimed, its lease recovery
             * process will eventually handle an abandoned PROCESSING row.
             */
            log.error(
                    "Email delivery consumer encountered an infrastructure failure.",
                    exception
            );

            channel.basicNack(
                    deliveryTag,
                    false,
                    true
            );
        }
    }


    private void validateMessageContentType(Message message) {

        MessageProperties properties = message.getMessageProperties();

        String contentType = properties.getContentType();

        if (!contentType.toLowerCase().startsWith(MessageProperties.CONTENT_TYPE_JSON)) {

            throw new InvalidEmailDeliveryMessageException(
                    "Email delivery event must have JSON content type."
            );
        }

        if (message.getBody().length == 0) {

            throw new InvalidEmailDeliveryMessageException(
                    "Email delivery event body is empty."
            );
        }
    }


    private void validateEvent(
            NotificationDeliveryRequestedEvent event
    ) {

        if (event == null) {
            throw new InvalidEmailDeliveryMessageException(
                    "Email delivery event is missing."
            );
        }

        if (event.schemaVersion() != 1) {
            throw new InvalidEmailDeliveryMessageException(
                    "Unsupported email delivery event schema version."
            );
        }

        if (!EXPECTED_EVENT_TYPE.equals(event.eventType())) {
            throw new InvalidEmailDeliveryMessageException(
                    "Unexpected email delivery event type."
            );
        }

        if (event.deliveryId() == null) {
            throw new InvalidEmailDeliveryMessageException(
                    "Delivery ID is required."
            );
        }

        if (event.notificationId() == null) {
            throw new InvalidEmailDeliveryMessageException(
                    "Notification ID is required."
            );
        }

        if (event.channel() != NotificationChannel.EMAIL) {
            throw new InvalidEmailDeliveryMessageException(
                    "Email queue only accepts EMAIL delivery requests."
            );
        }
    }
}
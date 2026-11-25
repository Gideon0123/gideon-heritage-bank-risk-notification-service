package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationContentFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryExecutionService {

    private final EmailDeliveryClaimService claimService;
    private final EmailDeliveryStateService stateService;
    private final NotificationContentEncryptionService encryptionService;
    private final SmtpEmailSender emailSender;

    /**
     * Processes one email delivery.
     * expectedNotificationId is supplied by RabbitMQ when the
     * call originates from an outbox delivery event. The scheduled
     * retry worker may pass null because it already reads the
     * delivery ID directly from the database.
     */
    public void process(
            UUID deliveryId,
            UUID expectedNotificationId,
            String workerId
    ) {

        Optional<ClaimedEmailDelivery> claimedOptional = claimService.claim(
                deliveryId,
                expectedNotificationId,
                workerId
        );

        if (claimedOptional.isEmpty()) {
            log.debug(
                    "Email delivery was skipped because it is already "
                            + "processing, completed or not due. deliveryId={}",
                    deliveryId
            );

            return;
        }

        ClaimedEmailDelivery claimed = claimedOptional.get();

        try {
            String recipient = encryptionService.decryptDestination(
                    claimed.encryptedDestination()
            );

            String content = encryptionService.decryptContent(
                    claimed.encryptedContent()
            );

            if (recipient == null || recipient.isBlank()) {
                throw new IllegalArgumentException("Decrypted email destination is empty.");
            }

            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Decrypted email content is empty.");
            }

            if (claimed.subject() == null || claimed.subject().isBlank()) {
                throw new IllegalArgumentException("Email subject is missing.");
            }

            NotificationContentFormat contentFormat = claimed.contentFormat();

            EmailSendResult result = emailSender.send(
                    claimed.deliveryId(),
                    recipient,
                    claimed.subject(),
                    content,
                    contentFormat
            );

            stateService.markSent(
                    claimed.deliveryId(),
                    claimed.workerId(),
                    result
            );

        } catch (MailException exception) {

            log.error(
                    """
                    SMTP DELIVERY FAILURE
                    Delivery ID: {}
                    Attempt: {}
                    Exception Type: {}
                    Exception Message: {}
                    """,
                    claimed.deliveryId(),
                    claimed.attemptNumber(),
                    exception.getClass().getName(),
                    exception.getMessage(),
                    exception
            );

            stateService.scheduleRetry(
                    claimed.deliveryId(),
                    claimed.workerId(),
                    exception
            );

        } catch (Exception exception) {

            /*
             * Invalid delivery data, decryption failure,
             * malformed message, or another non-SMTP failure.
             *
             * These failures are not automatically retried.
             */
            log.error(
                    "Email delivery failed permanently. "
                            + "deliveryId={}, attempt={}, exceptionType={}",
                    claimed.deliveryId(),
                    claimed.attemptNumber(),
                    exception.getClass().getSimpleName()
            );

            stateService.markFailed(
                    claimed.deliveryId(),
                    claimed.workerId(),
                    exception
            );
        }
    }
}
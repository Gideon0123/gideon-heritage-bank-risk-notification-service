package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationEmailProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationContentFormat;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender {

    private final JavaMailSender mailSender;
    private final NotificationEmailProperties properties;

    public EmailSendResult send(
            UUID deliveryId,
            String recipient,
            String subject,
            String content,
            NotificationContentFormat contentFormat
    ) {

        if (deliveryId == null) {
            throw new IllegalArgumentException("Email delivery ID is required.");
        }

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required.");
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Email subject is required.");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Email content is required.");
        }

        if (contentFormat == null) {
            throw new IllegalArgumentException("Email content format is required.");
        }

        properties.validate();

        MimeMessage mimeMessage = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    false,
                    StandardCharsets.UTF_8.name()
            );

            InternetAddress fromAddress = new InternetAddress(
                    properties.getFrom(),
                    properties.getFromName(),
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);

            if (properties.getReplyTo() != null && !properties.getReplyTo().isBlank()) {

                helper.setReplyTo(properties.getReplyTo());
            }

            helper.setTo(recipient);

            helper.setSubject(subject);

            boolean html = contentFormat == NotificationContentFormat.HTML;

            helper.setText(
                    content,
                    html
            );

            /*
             * Stable identifier for tracing and duplicate investigation.
             *
             * This does not provide exactly-once SMTP delivery.
             */
            mimeMessage.setHeader(
                    "X-GHB-Notification-Delivery-ID",
                    deliveryId.toString()
            );

            mimeMessage.setHeader(
                    "X-GHB-Notification-Provider",
                    properties.getProvider()
            );

            mimeMessage.setDisposition(Message.RecipientType.TO.toString());

        } catch (Exception exception) {

            /*
             * Message preparation errors are not SMTP transport
             * failures. The caller treats them as permanent failures.
             */
            throw new IllegalStateException(
                    "Could not prepare the notification email message.",
                    exception
            );
        }

        /*
         * Do not catch MailException here.
         *
         * The execution service needs to distinguish SMTP transport
         * failures so it can schedule retries.
         */
        mailSender.send(mimeMessage);

        String providerMessageId = null;

        try {
            providerMessageId = mimeMessage.getMessageID();
        } catch (MessagingException exception) {
            log.debug(
                    "SMTP provider did not expose a message ID. deliveryId={}",
                    deliveryId
            );
        }

        log.info(
                "Email submitted to SMTP provider. deliveryId={}, provider={}",
                deliveryId,
                properties.getProvider()
        );

        return new EmailSendResult(
                properties.getProvider(),
                providerMessageId
        );
    }
}
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationEmailProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationContentFormat;
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
        if (deliveryId == null
                || recipient == null || recipient.isBlank()
                || subject == null || subject.isBlank()
                || content == null || content.isBlank()
                || contentFormat == null) {

            throw new IllegalArgumentException("Email delivery information is incomplete.");
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
            helper.setTo(recipient);
            helper.setSubject(subject);

            if (properties.getReplyTo() != null && !properties.getReplyTo().isBlank()) {

                helper.setReplyTo(properties.getReplyTo());
            }

            boolean html = contentFormat == NotificationContentFormat.HTML;

            helper.setText(content, html);

            mimeMessage.setHeader(
                    "X-GHB-Notification-Delivery-ID",
                    deliveryId.toString()
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not prepare the notification email message.",
                    exception
            );
        }

        /*
         * Spring Mail transport errors propagate as MailException.
         * EmailDeliveryExecutionService catches them and schedules retry.
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

        return new EmailSendResult(
                properties.getProvider(),
                providerMessageId
        );
    }
}
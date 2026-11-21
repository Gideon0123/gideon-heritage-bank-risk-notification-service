package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.InboxEventRepository;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security.InboxPayloadEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboxEventService {

    private final InboxEventRepository inboxEventRepository;
    private final InboxPayloadEncryptionService encryptionService;

    /**
     * Stores an incoming Identity event exactly once.
     * This method is transactional. The RabbitMQ listener only
     * acknowledges the message after this method returns successfully,
     * meaning the database transaction has committed.
     *
     * @return true if a new event was stored;
     *         false if the event was already received.
     */
    @Transactional
    public boolean accept(
            String eventType,
            String messageId,
            UUID userId,
            String payload
    ) {
        validate(eventType, messageId, userId, payload);

        String encryptedPayload = encryptionService.encrypt(payload);

        int inserted = inboxEventRepository.insertIfAbsent(
                UUID.randomUUID(),
                eventType,
                messageId,
                userId,
                encryptedPayload,
                Instant.now()
        );

        return inserted == 1;
    }

    private void validate(
            String eventType,
            String messageId,
            UUID userId,
            String payload
    ) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Identity event type is required.");
        }

        if (messageId == null || messageId.isBlank() || messageId.length() > 255) {

            throw new IllegalArgumentException("Identity event message ID is invalid.");
        }

        if (userId == null) {
            throw new IllegalArgumentException("Identity event user ID is required.");
        }

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Identity event payload is required.");
        }
    }
}
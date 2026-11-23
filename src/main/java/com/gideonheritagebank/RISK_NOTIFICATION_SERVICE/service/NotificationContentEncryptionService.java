package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security.AesGcmEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationContentEncryptionService {

    private static final String CONTENT_PURPOSE = "NOTIFICATION_DELIVERY_CONTENT";
    private static final String DESTINATION_PURPOSE = "NOTIFICATION_DELIVERY_DESTINATION";

    private final AesGcmEncryptionService encryptionService;

    public String encryptContent(String content) {
        return encryptionService.encrypt(
                content,
                CONTENT_PURPOSE
        );
    }

    public String decryptContent(String encryptedContent) {
        return encryptionService.decrypt(
                encryptedContent,
                CONTENT_PURPOSE
        );
    }

    public String encryptDestination(String destination) {
        return encryptionService.encrypt(
                destination,
                DESTINATION_PURPOSE
        );
    }

    public String decryptDestination(String encryptedDestination) {
        return encryptionService.decrypt(
                encryptedDestination,
                DESTINATION_PURPOSE
        );
    }
}
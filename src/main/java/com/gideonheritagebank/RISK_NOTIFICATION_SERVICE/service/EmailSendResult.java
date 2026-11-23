package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

public record EmailSendResult(
        String provider,
        String providerMessageId
) {
}
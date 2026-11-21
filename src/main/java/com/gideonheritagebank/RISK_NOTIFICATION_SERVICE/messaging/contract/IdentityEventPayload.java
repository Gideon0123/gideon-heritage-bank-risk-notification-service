package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract;

import java.util.UUID;

public interface IdentityEventPayload {

    String eventType();

    UUID userId();
}
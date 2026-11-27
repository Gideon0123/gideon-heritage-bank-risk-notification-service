package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import java.util.UUID;

public record NotificationUnreadCountChangedEvent(UUID userId) {}
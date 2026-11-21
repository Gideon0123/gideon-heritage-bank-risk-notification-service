package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.utils;

import java.util.UUID;

public class TraceIdUtil {
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
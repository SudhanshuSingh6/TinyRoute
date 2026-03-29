package com.tinyroute.common.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateTimeUtil {

    private DateTimeUtil() {
        // Private constructor to prevent instantiation
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("UTC")); // Or default system timezone
    }
    
    public static boolean isExpired(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return false;
        }
        return expiresAt.isBefore(LocalDateTime.now());
    }
}

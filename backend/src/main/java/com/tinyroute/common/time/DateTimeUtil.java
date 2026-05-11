package com.tinyroute.common.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class

DateTimeUtil {

    private DateTimeUtil() {
    }
    
    public static boolean isExpired(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return false;
        }
        return expiresAt.isBefore(LocalDateTime.now());
    }
}

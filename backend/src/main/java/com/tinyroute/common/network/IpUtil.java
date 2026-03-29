package com.tinyroute.common.network;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtil {

    private IpUtil() {
        // Private constructor to prevent instantiation
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

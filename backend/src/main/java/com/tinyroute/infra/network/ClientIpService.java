package com.tinyroute.infra.network;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class ClientIpService {

    @Value("${app.environment:dev}")
    private String environment;

    public String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip;
        if (forwarded != null && !forwarded.isBlank()) {
            ip = forwarded.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return normalizeLocalIp(ip);
    }

    private String normalizeLocalIp(String ip) {

        if (!"prod".equalsIgnoreCase(environment)) {
            if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
                return "localhost";
            }
        }

        return ip;
    }

    public String hashIp(String ip) {

        if (ip == null || ip.isBlank()) {
            return "unknown";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            return "unknown";
        }
    }
}
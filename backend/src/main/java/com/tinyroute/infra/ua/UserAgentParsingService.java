package com.tinyroute.infra.ua;

import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

@Service
public class UserAgentParsingService {

    private static final String UNKNOWN = "Unknown";
    private static final Parser UA_PARSER;

    static {
        try {
            UA_PARSER = new Parser();
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to initialise ua-parser: " + e.getMessage());
        }
    }

    public ParsedUserAgent parse(String userAgentString) {
        if (userAgentString == null || userAgentString.isBlank()) {
            return new ParsedUserAgent(UNKNOWN, UNKNOWN, UNKNOWN);
        }

        try {
            Client client = UA_PARSER.parse(userAgentString);
            if (client == null || client.userAgent == null || client.os == null || client.device == null) {
                return new ParsedUserAgent(UNKNOWN, UNKNOWN, UNKNOWN);
            }

            String browser = safe(client.userAgent.family);
            String os = safe(client.os.family);
            String deviceType = mapDeviceType(safe(client.device.family));
            return new ParsedUserAgent(browser, os, deviceType);
        } catch (Exception e) {
            return new ParsedUserAgent(UNKNOWN, UNKNOWN, UNKNOWN);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private String mapDeviceType(String device) {
        if ("Other".equalsIgnoreCase(device)) {
            return "Desktop";
        }
        if ("Spider".equalsIgnoreCase(device)) {
            return "Bot";
        }
        return "Mobile";
    }

    public record ParsedUserAgent(String browser, String os, String deviceType) { }
}

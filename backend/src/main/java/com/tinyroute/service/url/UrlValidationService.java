package com.tinyroute.service.url;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UrlValidationService {

    private final UrlSafetyValidator urlSafetyValidator;

    public String validateAndNormalizeDestinationUrl(String rawUrl) {
        return urlSafetyValidator.validateAndNormalizeDestinationUrl(rawUrl);
    }
}

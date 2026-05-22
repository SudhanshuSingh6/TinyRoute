package com.tinyroute.infra.cache;

import com.tinyroute.entity.UrlStatus;

import java.time.LocalDateTime;

public record RedirectCacheEntry(
        Long          id,
        String        originalUrl,
        UrlStatus     status,
        LocalDateTime expiresAt,
        Integer       maxClicks,
        int           clickCount
) {}
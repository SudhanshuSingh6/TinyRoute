package com.tinyroute.infra.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyroute.url.entity.UrlMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectCacheService {

    private static final String KEY_PREFIX = "redirect:";

    private final StringRedisTemplate redisTemplate;

    @Qualifier("cacheObjectMapper")
    private final ObjectMapper cacheObjectMapper;

    @Value("${cache.redirect.ttl-seconds:300}")
    private long ttlSeconds;

    public Optional<RedirectCacheEntry> get(String shortUrl) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + shortUrl);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(cacheObjectMapper.readValue(json, RedirectCacheEntry.class));
        } catch (Exception e) {
            log.warn("Cache read failed for '{}' — evicting and falling back to DB: {}",
                    shortUrl, e.getMessage());
            evict(shortUrl);
            return Optional.empty();
        }
    }

    public void put(String shortUrl, UrlMapping urlMapping) {
        try {
            RedirectCacheEntry entry = toEntry(urlMapping);
            String json = cacheObjectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(KEY_PREFIX + shortUrl, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Cache write failed for '{}': {}", shortUrl, e.getMessage());
        }
    }

    public void evict(String shortUrl) {
        try {
            redisTemplate.delete(KEY_PREFIX + shortUrl);
        } catch (Exception e) {
            log.warn("Cache eviction failed for '{}': {}", shortUrl, e.getMessage());
        }
    }


    public RedirectCacheEntry toEntry(UrlMapping urlMapping) {
        return new RedirectCacheEntry(
                urlMapping.getId(),
                urlMapping.getOriginalUrl(),
                urlMapping.getStatus(),
                urlMapping.getExpiresAt(),
                urlMapping.getMaxClicks(),
                urlMapping.getClickCount(),
                urlMapping.getTotalClickCount()
        );
    }

    public UrlMapping toUrlMapping(RedirectCacheEntry entry) {
        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setId(entry.id());
        urlMapping.setOriginalUrl(entry.originalUrl());
        urlMapping.setStatus(entry.status());
        urlMapping.setExpiresAt(entry.expiresAt());
        urlMapping.setMaxClicks(entry.maxClicks());
        urlMapping.setClickCount(entry.clickCount());
        urlMapping.setTotalClickCount(entry.totalClickCount());
        return urlMapping;
    }
}
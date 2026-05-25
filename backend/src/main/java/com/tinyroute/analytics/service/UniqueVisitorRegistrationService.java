package com.tinyroute.analytics.service;

import com.tinyroute.analytics.repository.UrlUniqueVisitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UniqueVisitorRegistrationService {

    private final UrlUniqueVisitorRepository urlUniqueVisitorRepository;

    public boolean registerIfFirstVisit(Long urlMappingId, String ipHash, LocalDateTime firstSeenAt) {
        return urlUniqueVisitorRepository.insertUniqueVisitor(
                urlMappingId,
                ipHash,
                firstSeenAt
        ) > 0;
    }
}
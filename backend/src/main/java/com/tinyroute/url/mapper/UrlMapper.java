package com.tinyroute.url.mapper;

import com.tinyroute.url.dto.EditHistoryResponse;
import com.tinyroute.url.dto.UrlDetailsResponse;
import com.tinyroute.user.dto.PublicUrlDTO;
import com.tinyroute.url.entity.UrlEditHistory;
import com.tinyroute.url.entity.UrlMapping;
import org.springframework.stereotype.Component;

@Component
public class UrlMapper {

    public UrlDetailsResponse toUrlDetailsResponse(UrlMapping urlMapping) {
        UrlDetailsResponse dto = new UrlDetailsResponse();
        dto.setId(urlMapping.getId());
        dto.setOriginalUrl(urlMapping.getOriginalUrl());
        dto.setShortUrl(urlMapping.getShortUrl());
        dto.setTitle(urlMapping.getTitle());
        dto.setClickCount(urlMapping.getClickCount());
        dto.setCreatedAt(urlMapping.getCreatedDate());
        dto.setExpiresAt(urlMapping.getExpiresAt());
        dto.setMaxClicks(urlMapping.getMaxClicks());
        dto.setStatus(urlMapping.getStatus());
        return dto;
    }

    public EditHistoryResponse toEditHistoryResponse(UrlEditHistory history) {
        EditHistoryResponse dto = new EditHistoryResponse();
        dto.setId(history.getId());
        dto.setOldUrl(history.getOldUrl());
        dto.setChangedAt(history.getChangedAt());
        return dto;
    }
}
package com.tinyroute.mapper;

import com.tinyroute.dto.url.response.EditHistoryDTO;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.dto.user.response.PublicUrlDTO;
import com.tinyroute.entity.UrlEditHistory;
import com.tinyroute.entity.UrlMapping;
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
        dto.setCreatedDate(urlMapping.getCreatedDate());
        dto.setExpiresAt(urlMapping.getExpiresAt());
        dto.setLastClickedAt(urlMapping.getLastClickedAt());
        dto.setMaxClicks(urlMapping.getMaxClicks());
        dto.setStatus(urlMapping.getStatus());
        return dto;
    }

    public EditHistoryDTO toEditHistoryResponse(UrlEditHistory history) {
        EditHistoryDTO dto = new EditHistoryDTO();
        dto.setId(history.getId());
        dto.setOldUrl(history.getOldUrl());
        dto.setChangedAt(history.getChangedAt());
        return dto;
    }

    public PublicUrlDTO toPublicBioLinkResponse(UrlMapping urlMapping) {
        PublicUrlDTO dto = new PublicUrlDTO();
        dto.setShortUrl(urlMapping.getShortUrl());
        dto.setTitle(urlMapping.getTitle());
        dto.setOriginalUrl(urlMapping.getOriginalUrl());
        dto.setCreatedDate(urlMapping.getCreatedDate());
        return dto;
    }
}
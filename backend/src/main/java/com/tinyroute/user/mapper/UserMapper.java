package com.tinyroute.user.mapper;

import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.user.dto.PublicUrlDTO;
import com.tinyroute.user.dto.UserProfileDTO;
import com.tinyroute.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileDTO toUserProfileDTO(User user) {
        return toUserProfileDTO(user, user != null ? user.getBioPageViews() : null);
    }

    public UserProfileDTO toUserProfileDTO(User user, Long bioPageViewsOverride) {
        if (user == null) {
            return null;
        }

        UserProfileDTO dto = new UserProfileDTO();
        dto.setUsername(user.getUsername());
        dto.setBio(user.getBio());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBioPageViews(bioPageViewsOverride);
        return dto;
    }
    public PublicUrlDTO toPublicBioLinkResponse(UrlMapping urlMapping) {
        PublicUrlDTO dto = new PublicUrlDTO();
        dto.setShortUrl(urlMapping.getShortUrl());
        dto.setTitle(urlMapping.getTitle());
        dto.setOriginalUrl(urlMapping.getOriginalUrl());
        dto.setCreatedAt(urlMapping.getCreatedDate());
        return dto;
    }
}

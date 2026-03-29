package com.tinyroute.mapper;

import com.tinyroute.dto.user.UserProfileDTO;
import com.tinyroute.entity.User;
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
}

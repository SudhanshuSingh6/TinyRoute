package com.tinyroute.user.service;

import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.user.dto.UserProfileDTO;
import com.tinyroute.user.dto.PublicProfileResponse;
import com.tinyroute.user.dto.PublicUrlDTO;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.user.entity.User;
import com.tinyroute.exception.ErrorMessages;
import com.tinyroute.url.mapper.UrlMapper;
import com.tinyroute.user.mapper.UserMapper;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UrlMappingRepository urlMappingRepository;
    private final UrlMapper urlMapper;

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(ErrorMessages.USER_NOT_FOUND));
    }

    @Transactional
    public UserProfileDTO updateProfile(String username, String bio, String avatarUrl) {
        User user = findByUsername(username);

        if (bio != null) {
            String newBio = bio.trim();
            newBio = newBio.isBlank() ? null : newBio;

            if (!Objects.equals(user.getBio(), newBio)) {
                user.setBio(newBio);
            }
        }

        if (avatarUrl != null) {
            String newAvatar = avatarUrl.trim();
            newAvatar = newAvatar.isBlank() ? null : newAvatar;

            if (!Objects.equals(user.getAvatarUrl(), newAvatar)) {
                user.setAvatarUrl(newAvatar);
            }
        }

        return toProfileDto(user);
    }

    @Transactional
    public PublicProfileResponse getPublicProfile(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCodes.USER_NOT_FOUND,
                        ErrorMessages.USER_NOT_FOUND
                ));

        user.setBioPageViews(user.getBioPageViews() + 1);

        UserProfileDTO profile = userMapper.toUserProfileDTO(user);

        List<PublicUrlDTO> publicUrls = urlMappingRepository.findByUser(user).stream()
                .filter(this::isPubliclyAccessible)
                .map(userMapper::toPublicBioLinkResponse)
                .toList();

        PublicProfileResponse dto = new PublicProfileResponse();
        dto.setUsername(profile.getUsername());
        dto.setBio(profile.getBio());
        dto.setAvatarUrl(profile.getAvatarUrl());
        dto.setUrls(publicUrls);

        return dto;
    }


    public UserProfileDTO getProfile(String username) {
        return toProfileDto(findByUsername(username));
    }

    private UserProfileDTO toProfileDto(User user) {
        return userMapper.toUserProfileDTO(user);
    }

    private boolean isPubliclyAccessible(UrlMapping urlMapping) {
        if (urlMapping == null) {
            return false;
        }

        if (urlMapping.getStatus() != UrlStatus.ACTIVE) {
            return false;
        }
        if (urlMapping.getExpiresAt() != null && LocalDateTime.now().isAfter(urlMapping.getExpiresAt())) {
            return false;
        }

        return urlMapping.getMaxClicks() == null
                || urlMapping.getClickCount() < urlMapping.getMaxClicks();
    }
}
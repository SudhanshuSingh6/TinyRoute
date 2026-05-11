package com.tinyroute.service.user;

import com.tinyroute.dto.user.UserProfileDTO;
import com.tinyroute.dto.user.response.PublicProfileResponse;
import com.tinyroute.dto.user.response.PublicUrlDTO;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ErrorMessages;
import com.tinyroute.exception.UrlException;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.mapper.UserMapper;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
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
        boolean updated = false;

        if (bio != null) {
            String newBio = bio.trim();
            newBio = newBio.isBlank() ? null : newBio;

            if (!Objects.equals(user.getBio(), newBio)) {
                user.setBio(newBio);
                updated = true;
            }
        }

        if (avatarUrl != null) {
            String newAvatar = avatarUrl.trim();
            newAvatar = newAvatar.isBlank() ? null : newAvatar;

            if (!Objects.equals(user.getAvatarUrl(), newAvatar)) {
                user.setAvatarUrl(newAvatar);
                updated = true;
            }
        }

        if (updated) {
            userRepository.save(user);
        }

        return toProfileDto(user);
    }

    @Transactional
    public PublicProfileResponse getPublicProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> UrlException.notFound("Public profile not found."));

        user.setBioPageViews(user.getBioPageViews() + 1);

        UserProfileDTO profile = userMapper.toUserProfileDTO(user);

        List<PublicUrlDTO> publicUrls = urlMappingRepository.findByUser(user).stream()
                .filter(this::isPubliclyAccessible)
                .map(urlMapper::toPublicBioLinkResponse)
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
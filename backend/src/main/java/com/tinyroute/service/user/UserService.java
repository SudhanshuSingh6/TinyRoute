package com.tinyroute.service.user;

import com.tinyroute.dto.auth.request.LoginRequest;
import com.tinyroute.dto.auth.request.RegisterRequest;
import com.tinyroute.dto.auth.response.JwtAuthenticationResponse;
import com.tinyroute.dto.user.UserProfileDTO;
import com.tinyroute.dto.user.response.PublicProfileResponse;
import com.tinyroute.dto.user.response.PublicUrlDTO;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.EmailAlreadyExistsException;
import com.tinyroute.exception.UsernameAlreadyExistsException;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.mapper.UserMapper;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.repository.user.UserRepository;
import com.tinyroute.service.url.UrlLookupService;
import com.tinyroute.security.jwt.JwtService;
import com.tinyroute.security.UserDetailsImpl;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@AllArgsConstructor
public class UserService {

    private UserRepository userRepository;
    private UserMapper userMapper;
    private UrlMappingRepository urlMappingRepository;
    private UrlMapper urlMapper;

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(
                () -> new UsernameNotFoundException("User not found with username: " + username));
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
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            return null;
        }

        userRepository.incrementBioPageViews(username);

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

    public UserProfileDTO toProfileDto(User user) {
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
        return urlMapping.getMaxClicks() == null || urlMapping.getClickCount() < urlMapping.getMaxClicks();
    }
}
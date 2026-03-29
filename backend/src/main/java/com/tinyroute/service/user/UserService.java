package com.tinyroute.service.user;

import com.tinyroute.dto.auth.request.LoginRequest;
import com.tinyroute.dto.auth.request.RegisterRequest;
import com.tinyroute.dto.auth.response.JwtAuthenticationResponse;
import com.tinyroute.dto.user.UserProfileDTO;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.EmailAlreadyExistsException;
import com.tinyroute.exception.UsernameAlreadyExistsException;
import com.tinyroute.mapper.UserMapper;
import com.tinyroute.repository.user.UserRepository;
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

import java.util.Locale;
import java.util.Objects;

@Service
@AllArgsConstructor
public class UserService {

    private UserRepository userRepository;
    private UserMapper userMapper;

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
    public void incrementBioPageViews(String username) {
        userRepository.incrementBioPageViews(username);
    }

    public UserProfileDTO getProfile(String username) {
        return toProfileDto(findByUsername(username));
    }

    public UserProfileDTO toProfileDto(User user) {
        return userMapper.toUserProfileDTO(user);
    }
}
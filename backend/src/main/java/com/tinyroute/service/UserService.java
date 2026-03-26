package com.tinyroute.service;

import com.tinyroute.dtos.LoginRequest;
import com.tinyroute.dtos.RegisterRequest;
import com.tinyroute.dtos.UserProfileDTO;
import com.tinyroute.models.Role;
import com.tinyroute.models.User;
import com.tinyroute.repository.UserRepository;
import com.tinyroute.security.jwt.JwtAuthenticationResponse;
import com.tinyroute.security.jwt.JwtUtils;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@AllArgsConstructor
public class UserService {

    private PasswordEncoder passwordEncoder;
    private UserRepository userRepository;
    private AuthenticationManager authenticationManager;
    private JwtUtils jwtUtils;

    @Transactional
    public void registerUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username '" + request.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered.");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);
    }

    public JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(), loginRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String jwt = jwtUtils.generateToken(userDetails);
        return new JwtAuthenticationResponse(jwt);
    }

    public User findByUsername(String name) {
        return userRepository.findByUsername(name).orElseThrow(
                () -> new UsernameNotFoundException("User not found with username: " + name));
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
        UserProfileDTO dto = new UserProfileDTO();
        dto.setUsername(user.getUsername());
        dto.setBio(user.getBio());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBioPageViews(user.getBioPageViews());
        return dto;
    }
}
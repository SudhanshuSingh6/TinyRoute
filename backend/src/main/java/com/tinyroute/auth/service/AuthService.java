package com.tinyroute.auth.service;

import com.tinyroute.auth.dto.LoginRequest;
import com.tinyroute.auth.dto.RegisterRequest;
import com.tinyroute.auth.dto.AuthResponse;
import com.tinyroute.user.entity.Role;
import com.tinyroute.user.entity.User;
import com.tinyroute.exception.AlreadyExistsException;
import com.tinyroute.exception.ApiException;
import com.tinyroute.user.repository.UserRepository;
import com.tinyroute.security.UserDetailsImpl;
import com.tinyroute.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService           jwtService;
    private final RefreshTokenService refreshTokenService;    // NEW

    @Transactional
    public AuthResponse authenticateUser(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        String accessToken = jwtService.generateAccessToken(userDetails);

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found after authentication: " + userDetails.getUsername()
                ));

        String rawRefreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, rawRefreshToken);
    }


    @Transactional
    public void registerUser(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        String email    = normalizeEmail(request.getEmail());

        if (userRepository.existsByUsername(username)) {
            throw AlreadyExistsException.username();
        }
        if (userRepository.existsByEmail(email)) {
            throw AlreadyExistsException.email();
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ROLE_USER);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            if (userRepository.existsByUsername(username)) throw AlreadyExistsException.username();
            if (userRepository.existsByEmail(email))    throw AlreadyExistsException.email();
            throw new ApiException(HttpStatus.CONFLICT, "REGISTRATION_FAILED",
                    "Could not complete registration.");
        }
    }


    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }


    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
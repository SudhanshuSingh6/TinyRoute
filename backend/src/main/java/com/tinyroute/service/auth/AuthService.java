package com.tinyroute.service.auth;

import com.tinyroute.dto.auth.request.LoginRequest;
import com.tinyroute.dto.auth.request.RegisterRequest;
import com.tinyroute.dto.auth.response.JwtAuthenticationResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.EmailAlreadyExistsException;
import com.tinyroute.exception.UsernameAlreadyExistsException;
import com.tinyroute.repository.user.UserRepository;
import com.tinyroute.security.UserDetailsImpl;
import com.tinyroute.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public JwtAuthenticationResponse authenticateUser(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String jwt = jwtService.generateToken(userDetails);

        return new JwtAuthenticationResponse(jwt);
    }

    @Transactional
    public void registerUser(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        String email    = normalizeEmail(request.getEmail());

        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("Username '" + username + "' is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email '" + email + "' is already registered.");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ROLE_USER);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            if (userRepository.existsByUsername(username)) {
                throw new UsernameAlreadyExistsException("Username '" + username + "' is already taken.");
            }
            if (userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException("Email '" + email + "' is already registered.");
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REGISTRATION_FAILED",
                    "Could not complete registration."
            );
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
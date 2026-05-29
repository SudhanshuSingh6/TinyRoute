package com.tinyroute.service.auth;

import com.tinyroute.auth.dto.AuthResponse;
import com.tinyroute.auth.dto.LoginRequest;
import com.tinyroute.auth.dto.RegisterRequest;
import com.tinyroute.auth.service.AuthService;
import com.tinyroute.auth.service.RefreshTokenService;
import com.tinyroute.exception.AlreadyExistsException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.security.UserDetailsImpl;
import com.tinyroute.security.jwt.JwtService;
import com.tinyroute.user.entity.Role;
import com.tinyroute.user.entity.User;
import com.tinyroute.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(String username) {
        User u = new User();
        u.setId(1L);
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPassword("hashed");
        u.setRole(Role.ROLE_USER);
        return u;
    }

    @Test
    void authenticateUser_validCredentials_returnsAuthResponse() {
        User u = user("alice");
        UserDetailsImpl principal = UserDetailsImpl.build(u);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        when(refreshTokenService.createRefreshToken(u)).thenReturn("refresh-raw");

        AuthResponse response = authService.authenticateUser(request);

        assertEquals("access-token", response.getToken());
        assertEquals("refresh-raw", response.getRefreshToken());
    }

    @Test
    void authenticateUser_badCredentials_propagates() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class, () -> authService.authenticateUser(request));
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void registerUser_newUser_savesWithEncodedPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob");
        request.setEmail("Bob@Example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-pw");

        authService.registerUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("bob", saved.getUsername());
        assertEquals("bob@example.com", saved.getEmail()); // normalized to lowercase
        assertEquals("encoded-pw", saved.getPassword());
        assertNotEquals("password123", saved.getPassword());
        assertEquals(Role.ROLE_USER, saved.getRole());
    }

    @Test
    void registerUser_duplicateUsername_throwsConflict() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob");
        request.setEmail("bob@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("bob")).thenReturn(true);

        AlreadyExistsException ex = assertThrows(AlreadyExistsException.class,
                () -> authService.registerUser(request));
        assertEquals(ErrorCodes.USERNAME_ALREADY_EXISTS, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_duplicateEmail_throwsConflict() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob");
        request.setEmail("bob@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        AlreadyExistsException ex = assertThrows(AlreadyExistsException.class,
                () -> authService.registerUser(request));
        assertEquals(ErrorCodes.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void logout_revokesRefreshToken() {
        authService.logout("raw-refresh-token");
        verify(refreshTokenService).revoke("raw-refresh-token");
    }
}

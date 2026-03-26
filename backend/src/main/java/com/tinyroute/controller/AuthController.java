package com.tinyroute.controller;

import com.tinyroute.dtos.LoginRequest;
import com.tinyroute.dtos.RegisterRequest;
import com.tinyroute.dtos.UserProfileDTO;
import com.tinyroute.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Tag(name = "Auth", description = "Register, login and manage your profile")
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private UserService userService;

    @Operation(summary = "Login", description = "Authenticate and receive a JWT token.")
    @ApiResponse(responseCode = "200", description = "Login successful, token returned")
    @ApiResponse(responseCode = "400", description = "Validation failed — username or password blank")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/public/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            return ResponseEntity.ok(userService.authenticateUser(loginRequest));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Username or password is incorrect.");
        }
    }

    @Operation(summary = "Register", description = "Create a new user account.")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Username or email already taken")
    @PostMapping("/public/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        userService.registerUser(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
    }

    @Operation(summary = "Update profile", description = "Update your bio and avatar URL.")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody Map<String, String> request,
            Principal principal) {
        try {
            String bio       = request.get("bio");
            String avatarUrl = request.get("avatarUrl");
            UserProfileDTO dto = userService.updateProfile(principal.getName(), bio, avatarUrl);
            return ResponseEntity.ok(dto);
        } catch (UsernameNotFoundException e) {
            log.warn("Profile update for non-existent user '{}'", principal.getName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Your account could not be found. Please log in again.");
        }
    }

    @Operation(summary = "Get my profile", description = "Returns your own profile including bio page view count.")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Principal principal) {
        try {
            UserProfileDTO dto = userService.getProfile(principal.getName());
            return ResponseEntity.ok(dto);
        } catch (UsernameNotFoundException e) {
            log.warn("Profile access for non-existent user '{}'", principal.getName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Your account could not be found. Please log in again.");
        }
    }
}
package com.tinyroute.controller;

import com.tinyroute.dtos.LoginRequest;
import com.tinyroute.dtos.RegisterRequest;
import com.tinyroute.dtos.UserProfileDTO;
import com.tinyroute.models.Role;
import com.tinyroute.models.User;
import com.tinyroute.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Tag(name = "Auth", description = "Register, login and manage your profile")
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private UserService userService;

    @Operation(summary = "Login", description = "Authenticate and receive a JWT token.")
    @ApiResponse(responseCode = "200", description = "Login successful, token returned")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/public/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(userService.authenticateUser(loginRequest));
    }

    @Operation(summary = "Register", description = "Create a new user account.")
    @ApiResponse(responseCode = "200", description = "User registered successfully")
    @PostMapping("/public/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(registerRequest.getPassword());
        user.setEmail(registerRequest.getEmail());
        user.setRole(Role.ROLE_USER);
        userService.registerUser(user);
        return ResponseEntity.ok("User registered successfully");
    }

    @Operation(summary = "Update profile", description = "Update your bio and avatar URL.")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/profile")
    public ResponseEntity<UserProfileDTO> updateProfile(
            @RequestBody Map<String, String> request,
            Principal principal) {
        String bio       = request.get("bio");
        String avatarUrl = request.get("avatarUrl");
        UserProfileDTO dto = userService.updateProfile(principal.getName(), bio, avatarUrl);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Get my profile", description = "Returns your own profile including bio page view count.")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/profile")
    public ResponseEntity<UserProfileDTO> getProfile(Principal principal) {
        UserProfileDTO dto = userService.getProfile(principal.getName());
        return ResponseEntity.ok(dto);
    }
}
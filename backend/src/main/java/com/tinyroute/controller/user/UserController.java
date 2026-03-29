package com.tinyroute.controller.user;

import com.tinyroute.dto.user.UserProfileDTO;
import com.tinyroute.dto.user.request.UpdateProfileRequest;
import com.tinyroute.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Tag(name = "Profile", description = "Manage your profile")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/auth/profile")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Update profile", description = "Update your bio and avatar URL.")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @PreAuthorize("hasRole('USER')")
    @PutMapping
    public ResponseEntity<UserProfileDTO> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Principal principal) {
        UserProfileDTO dto = userService.updateProfile(
                principal.getName(),
                request.getBio(),
                request.getAvatarUrl()
        );
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Get my profile", description = "Returns your own profile including bio page view count.")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public ResponseEntity<UserProfileDTO> getProfile(Principal principal) {
        return ResponseEntity.ok(userService.getProfile(principal.getName()));
    }
}

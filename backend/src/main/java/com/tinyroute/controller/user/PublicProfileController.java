package com.tinyroute.controller.user;

import com.tinyroute.dto.user.response.PublicProfileResponse;
import com.tinyroute.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public Profile", description = "Public user profile and visible links")
@RestController
@RequestMapping("/api/public/users")
@RequiredArgsConstructor
public class PublicProfileController {

    private final UserService userService;

    @Operation(
            summary = "Get public user profile",
            description = "Returns public profile details and all publicly accessible links for a given username."
    )
    @ApiResponse(responseCode = "200", description = "Public profile returned successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{username}")
    public ResponseEntity<PublicProfileResponse> getPublicUserProfile(@PathVariable String username) {
        return ResponseEntity.ok(userService.getPublicProfile(username));
    }
}
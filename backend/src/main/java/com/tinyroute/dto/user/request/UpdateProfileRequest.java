package com.tinyroute.dto.user.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 300, message = "Bio must be 300 characters or fewer.")
    private String bio;

    @Size(max = 500, message = "Avatar URL must be 500 characters or fewer.")
    @Pattern(
            regexp = "^$|https?://.*",
            message = "Avatar URL must be a valid HTTP or HTTPS URL, or empty to clear it."
    )
    private String avatarUrl;
}

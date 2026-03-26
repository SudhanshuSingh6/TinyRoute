package com.tinyroute.dtos;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class UpdateProfileRequest {

    @Size(max = 300, message = "Bio must be 300 characters or fewer.")
    private String bio;

    @Size(max = 500, message = "Avatar URL must be 500 characters or fewer.")
    @URL(message = "Avatar URL must be a valid URL")
    private String avatarUrl;
}
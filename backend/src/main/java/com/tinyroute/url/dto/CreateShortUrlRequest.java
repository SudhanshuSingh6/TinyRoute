package com.tinyroute.url.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class CreateShortUrlRequest {

    @NotBlank(message = "originalUrl is required")
    @URL(message = "originalUrl must be a valid URL")
    @Size(max = 2048, message = "originalUrl must be 2048 characters or fewer")
    private String originalUrl;

    @Pattern(regexp = "^$|^[a-zA-Z0-9_-]{3,50}$", message = "customAlias must be 3-50 characters and may only contain letters, numbers, hyphens, and underscores")
    private String customAlias;

    @Future(message = "expiresAt must be a future date and time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiresAt;

    @Size(max = 150, message = "title must be 150 characters or fewer")
    private String title;
}
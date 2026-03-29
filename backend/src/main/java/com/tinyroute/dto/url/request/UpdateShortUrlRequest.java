package com.tinyroute.dto.url.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class UpdateShortUrlRequest {

    @NotBlank(message = "originalUrl is required")
    @URL(message = "originalUrl must be a valid URL")
    @Size(max = 2048, message = "originalUrl must be 2048 characters or fewer")
    private String originalUrl;

    @Size(max = 150, message = "title must be 150 characters or fewer")
    private String title;

}

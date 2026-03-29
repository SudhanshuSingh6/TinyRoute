package com.tinyroute.dto.url.request;

import jakarta.validation.constraints.FutureOrPresent;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class UpdateExpiryRequest {

    @FutureOrPresent(message = "expiresAt cannot be in the past")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiresAt;
}
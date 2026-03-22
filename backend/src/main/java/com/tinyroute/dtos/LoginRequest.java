package com.tinyroute.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 30, message = "Username must not exceed 30 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 100, message = "Password must not exceed 100 characters")
    private String password;
}
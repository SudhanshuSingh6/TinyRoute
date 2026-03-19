package com.tinyroute.controller;

import com.tinyroute.dtos.LoginRequest;
import com.tinyroute.dtos.RegisterRequest;
import com.tinyroute.models.Role;
import com.tinyroute.models.User;
import com.tinyroute.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {
    private UserService userService;

    @PostMapping("/public/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(userService.authenticateUser(loginRequest));
    }

    @PostMapping("/public/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(registerRequest.getPassword());
        user.setEmail(registerRequest.getEmail());
        user.setRole(Role.ROLE_USER); // enum instead of string
        userService.registerUser(user);
        return ResponseEntity.ok("User registered successfully");
    }
}
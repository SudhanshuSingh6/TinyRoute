package com.tinyroute.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_username", columnList = "username", unique = true),
                @Index(name = "idx_email",    columnList = "email",    unique = true)
        }
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String username;
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role = Role.ROLE_USER;

    private String bio;
    private String avatarUrl;
    private long bioPageViews = 0;
}
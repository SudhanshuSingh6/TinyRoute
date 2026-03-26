package com.tinyroute.entity;

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

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.ROLE_USER;

    @Column(length = 300)
    private String bio;

    @Column(length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    private long bioPageViews = 0;
}
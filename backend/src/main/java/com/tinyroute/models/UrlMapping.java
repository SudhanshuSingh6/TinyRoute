package com.tinyroute.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
public class UrlMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String originalUrl;
    private String shortUrl;
    private String customAlias;
    private String title;                                      // user-friendly label
    private int clickCount = 0;
    private LocalDateTime createdDate;
    private LocalDateTime expiresAt;
    private Integer maxClicks;
    private LocalDateTime lastClickedAt;

    private boolean isPublic = true;                           // NEW — show on bio page or not

    @Enumerated(EnumType.STRING)
    private UrlStatus status = UrlStatus.ACTIVE;               // NEW — state machine

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "urlMapping")
    private List<ClickEvent> clickEvents;
}
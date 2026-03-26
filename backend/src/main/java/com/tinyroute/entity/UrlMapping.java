package com.tinyroute.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(
        name = "url_mapping",
        indexes = {
                @Index(name = "idx_short_url",  columnList = "shortUrl",  unique = true),
                @Index(name = "idx_user_id",    columnList = "user_id"),
                @Index(name = "idx_status",     columnList = "status"),
                @Index(name = "idx_expires_at", columnList = "expiresAt"),
                // soft delete filter
                @Index(name = "idx_is_deleted", columnList = "isDeleted")
        }
)
public class UrlMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalUrl;
    private String shortUrl;
    private String customAlias;
    private String title;
    private int clickCount = 0;
    private LocalDateTime createdDate;
    private LocalDateTime expiresAt;
    private Integer maxClicks;
    private LocalDateTime lastClickedAt;
    private boolean isPublic = true;

    @Enumerated(EnumType.STRING)
    private UrlStatus status = UrlStatus.ACTIVE;

    private boolean isDeleted = false;
    private LocalDateTime deletedAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "urlMapping")
    private List<ClickEvent> clickEvents;
}
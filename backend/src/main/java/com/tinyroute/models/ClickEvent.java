package com.tinyroute.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(
        name = "click_event",
        indexes = {
                @Index(name = "idx_click_url_mapping", columnList = "url_mapping_id"),
                @Index(name = "idx_click_date",        columnList = "clickDate"),
                @Index(name = "idx_click_ip_hash",     columnList = "url_mapping_id,ipHash")
        }
)
public class ClickEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime clickDate;
    private String country;
    private String city;
    private String browser;
    private String os;
    private String deviceType;
    private String referrer;
    private String language;
    private String ipHash;
    private boolean isUniqueClick;

    @ManyToOne
    @JoinColumn(name = "url_mapping_id")
    private UrlMapping urlMapping;
}
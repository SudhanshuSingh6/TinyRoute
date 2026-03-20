package com.tinyroute.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
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

    // request metadata
    private String referrer;         // from Referer header — where click came from
    private String language;         // from Accept-Language header

    private String ipHash;
    private boolean isUniqueClick;   // unique ip

    @ManyToOne
    @JoinColumn(name = "url_mapping_id")
    private UrlMapping urlMapping;
}
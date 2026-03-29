package com.tinyroute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "click_event",
        indexes = {
                @Index(name = "idx_click_url_mapping", columnList = "url_mapping_id"),
                @Index(name = "idx_click_date", columnList = "click_date"),
                @Index(name = "idx_click_ip_hash", columnList = "url_mapping_id,ip_hash")
        }
)
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "click_date", nullable = false)
    private LocalDateTime clickDate;

    private String country;
    private String city;
    private String browser;
    private String os;
    private String deviceType;
    private String referrer;
    private String language;
    @Column(name = "ip_hash")
    private String ipHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "url_mapping_id", nullable = false)
    private UrlMapping urlMapping;
}
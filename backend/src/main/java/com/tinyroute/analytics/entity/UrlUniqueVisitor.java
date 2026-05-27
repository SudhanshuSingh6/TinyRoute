package com.tinyroute.analytics.entity;

import com.tinyroute.url.entity.UrlMapping;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "url_unique_visitor",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_unique_visitor_url_ip",
                        columnNames = {"url_mapping_id", "ip_hash"}
                )
        },
        indexes = {
                @Index(name = "idx_unique_visitor_url", columnList = "url_mapping_id"),
                @Index(name = "idx_unique_visitor_ip", columnList = "ip_hash")
        }
)
public class UrlUniqueVisitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "url_mapping_id", nullable = false)
    private UrlMapping urlMapping;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(nullable = false)
    private LocalDateTime firstSeenAt;
}

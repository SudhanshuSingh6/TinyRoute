package com.tinyroute.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "url_edit_history",
        indexes = {
                @Index(name = "idx_edit_history_url",  columnList = "url_mapping_id"),
                @Index(name = "idx_edit_history_date", columnList = "changedAt")
        }
)
public class UrlEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String oldUrl;
    private LocalDateTime changedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_mapping_id", nullable = false)
    private UrlMapping urlMapping;
}

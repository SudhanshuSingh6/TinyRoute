package com.tinyroute.url.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EditHistoryResponse {
    private Long id;
    private String oldUrl;
    private LocalDateTime changedAt;
}
package com.tinyroute.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UrlEditHistoryDTO {
    private Long id;
    private String oldUrl;
    private LocalDateTime changedAt;
}
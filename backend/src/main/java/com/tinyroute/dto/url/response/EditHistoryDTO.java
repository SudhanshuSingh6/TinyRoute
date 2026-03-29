package com.tinyroute.dto.url.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EditHistoryDTO {
    private Long id;
    private String oldUrl;
    private LocalDateTime changedAt;
}
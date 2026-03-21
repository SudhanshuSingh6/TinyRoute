package com.tinyroute.dtos;

import lombok.Data;

@Data
public class UserProfileDTO {
    private String username;
    private String bio;
    private String avatarUrl;
    private long bioPageViews;
}
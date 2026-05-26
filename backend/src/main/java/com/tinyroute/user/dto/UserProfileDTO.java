package com.tinyroute.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileDTO {
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private Long bioPageViews;
}
package com.tinyroute.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileDTO {
    private String username;
    private String email;       // included on private profile; omitted (null) on public bio page
    private String avatarUrl;
    private String bio;         // optional; if null, frontend can show links only
    private Long bioPageViews;
}

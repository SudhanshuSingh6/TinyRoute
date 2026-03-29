package com.tinyroute.dto.user.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tinyroute.dto.user.UserProfileDTO;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicPageResponse {
    private UserProfileDTO profile;
    private List<PublicUrlDTO> urls;
}

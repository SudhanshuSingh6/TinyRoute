package com.tinyroute.dtos;

import lombok.Data;

import java.util.List;

@Data
public class BioPageDTO {
    private UserProfileDTO profile;
    private List<UrlMappingDTO> urls;
}
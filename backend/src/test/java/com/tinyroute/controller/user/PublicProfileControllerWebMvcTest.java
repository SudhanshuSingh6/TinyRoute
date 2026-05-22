package com.tinyroute.controller.user;

import com.tinyroute.dto.user.response.PublicProfileResponse;
import com.tinyroute.dto.user.response.PublicUrlDTO;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PublicProfileControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getPublicUserProfile_validUsername_returns200AndProfileData() throws Exception {
        PublicUrlDTO urlDto = new PublicUrlDTO();
        urlDto.setShortUrl("abc12345");
        urlDto.setTitle("My Blog");
        urlDto.setOriginalUrl("https://myblog.com");
        urlDto.setCreatedDate(LocalDateTime.of(2026, 5, 20, 10, 0));

        PublicProfileResponse mockResponse = new PublicProfileResponse();
        mockResponse.setUsername("alice");
        mockResponse.setBio("Software Engineer");
        mockResponse.setAvatarUrl("https://example.com/alice.png");
        mockResponse.setUrls(List.of(urlDto));

        when(userService.getPublicProfile(eq("alice"))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/public/users/alice")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.bio").value("Software Engineer"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/alice.png"))
                .andExpect(jsonPath("$.urls[0].shortUrl").value("abc12345"))
                .andExpect(jsonPath("$.urls[0].title").value("My Blog"))
                .andExpect(jsonPath("$.urls[0].originalUrl").value("https://myblog.com"));
    }

    @Test
    void getPublicUserProfile_missingUser_returns404() throws Exception {
        when(userService.getPublicProfile(eq("missing")))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        mockMvc.perform(get("/api/public/users/missing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }
}

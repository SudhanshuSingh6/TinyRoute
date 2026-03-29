package com.tinyroute.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyroute.dto.user.UserProfileDTO;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    void getProfile_validPrincipal_returns200() throws Exception {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setUsername("alice");
        dto.setBio("builder");
        dto.setAvatarUrl("https://example.com/avatar.png");
        dto.setBioPageViews(25L);
        when(userService.getProfile("alice")).thenReturn(dto);

        mockMvc.perform(get("/api/auth/profile").principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.bioPageViews").value(25));
    }

    @Test
    void updateProfile_whenUserMissing_returns404() throws Exception {
        when(userService.updateProfile(eq("alice"), any(), any()))
                .thenThrow(new UsernameNotFoundException("missing"));

        mockMvc.perform(put("/api/auth/profile")
                        .principal(() -> "alice")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateProfilePayload("bio", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    private record UpdateProfilePayload(String bio, String avatarUrl) {
    }
}

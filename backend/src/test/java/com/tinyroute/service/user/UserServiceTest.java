package com.tinyroute.service.user;

import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.url.mapper.UrlMapper;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.user.dto.PublicProfileResponse;
import com.tinyroute.user.dto.PublicUrlDTO;
import com.tinyroute.user.dto.UserProfileDTO;
import com.tinyroute.user.entity.Role;
import com.tinyroute.user.entity.User;
import com.tinyroute.user.mapper.UserMapper;
import com.tinyroute.user.repository.UserRepository;
import com.tinyroute.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private UrlMapper urlMapper;

    @InjectMocks
    private UserService userService;

    private User user(String username) {
        User u = new User();
        u.setId(1L);
        u.setUsername(username);
        u.setRole(Role.ROLE_USER);
        u.setBioPageViews(5L);
        return u;
    }

    private UserProfileDTO profileDto(String username) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setUsername(username);
        return dto;
    }

    @Test
    void findByUsername_found_returnsUser() {
        User u = user("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        assertEquals(u, userService.findByUsername("alice"));
    }

    @Test
    void findByUsername_notFound_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> userService.findByUsername("ghost"));
    }

    @Test
    void getProfile_existingUser_returnsProfileDto() {
        User u = user("alice");
        UserProfileDTO dto = profileDto("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        when(userMapper.toUserProfileDTO(u)).thenReturn(dto);

        assertEquals(dto, userService.getProfile("alice"));
    }

    @Test
    void getProfile_unknownUser_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> userService.getProfile("ghost"));
    }

    @Test
    void updateProfile_validRequest_updatesBioAndAvatar() {
        User u = user("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        when(userMapper.toUserProfileDTO(u)).thenReturn(profileDto("alice"));

        userService.updateProfile("alice", "new bio", "https://cdn.example.com/a.png");

        assertEquals("new bio", u.getBio());
        assertEquals("https://cdn.example.com/a.png", u.getAvatarUrl());
    }

    @Test
    void updateProfile_unknownUser_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> userService.updateProfile("ghost", "bio", null));
    }

    @Test
    void getPublicProfile_existingUser_incrementsBioPageViewsAndReturnsPublicUrls() {
        User u = user("alice");

        UrlMapping publicUrl = new UrlMapping();
        publicUrl.setStatus(UrlStatus.ACTIVE);
        publicUrl.setClickCount(0);
        publicUrl.setMaxClicks(100);
        publicUrl.setShortUrl("abc123");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        when(userMapper.toUserProfileDTO(u)).thenReturn(profileDto("alice"));
        when(urlMappingRepository.findByUser(u)).thenReturn(List.of(publicUrl));
        when(userMapper.toPublicBioLinkResponse(publicUrl)).thenReturn(new PublicUrlDTO());

        PublicProfileResponse response = userService.getPublicProfile("alice");

        assertEquals("alice", response.getUsername());
        assertEquals(1, response.getUrls().size());
        // View count is bumped via an atomic UPDATE, not an in-memory read-modify-write.
        verify(userRepository).incrementBioPageViews(u.getId());
    }

    @Test
    void getPublicProfile_unknownUser_throwsApiException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> userService.getPublicProfile("ghost"));
        assertEquals(ErrorCodes.USER_NOT_FOUND, ex.getErrorCode());
    }
}

package com.tinyroute.repository;

import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UrlMappingRepositoryTest {

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // runs before every test — creates a fresh user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@test.com");
        testUser.setPassword("password123");
        userRepository.save(testUser);
    }

    //
    // TEST 1: save a URL and find it by short code
    //
    @Test
    void findByShortUrl_returnsCorrectMapping() {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl("https://google.com");
        mapping.setShortUrl("abc12345");
        mapping.setCreatedDate(LocalDateTime.now());
        mapping.setUser(testUser);
        urlMappingRepository.save(mapping);

        UrlMapping found = urlMappingRepository.findByShortUrl("abc12345");

        assertNotNull(found);
        assertEquals("https://google.com", found.getOriginalUrl());
        assertEquals("abc12345", found.getShortUrl());
    }

    //
    // TEST 2: short url that doesnt exist returns null
    //
    @Test
    void findByShortUrl_whenNotFound_returnsNull() {
        UrlMapping found = urlMappingRepository.findByShortUrl("doesnotexist");
        assertNull(found);
    }

    //
    // TEST 3: find all URLs belonging to a user
    //
    @Test
    void findByUser_returnsAllUrlsForUser() {
        UrlMapping mapping1 = new UrlMapping();
        mapping1.setOriginalUrl("https://google.com");
        mapping1.setShortUrl("short001");
        mapping1.setCreatedDate(LocalDateTime.now());
        mapping1.setUser(testUser);
        urlMappingRepository.save(mapping1);

        UrlMapping mapping2 = new UrlMapping();
        mapping2.setOriginalUrl("https://github.com");
        mapping2.setShortUrl("short002");
        mapping2.setCreatedDate(LocalDateTime.now());
        mapping2.setUser(testUser);
        urlMappingRepository.save(mapping2);

        List<UrlMapping> urls = urlMappingRepository.findByUser(testUser);

        assertEquals(2, urls.size());
    }

    //
    // TEST 4: user with no URLs returns empty list
    //
    @Test
    void findByUser_whenNoUrls_returnsEmptyList() {
        List<UrlMapping> urls = urlMappingRepository.findByUser(testUser);
        assertTrue(urls.isEmpty());
    }

    //
    // TEST 5: save custom alias and find it
    //
    @Test
    void findByShortUrl_withCustomAlias_returnsCorrectMapping() {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl("https://youtube.com");
        mapping.setShortUrl("myalias");
        mapping.setCustomAlias("myalias");
        mapping.setCreatedDate(LocalDateTime.now());
        mapping.setUser(testUser);
        urlMappingRepository.save(mapping);

        UrlMapping found = urlMappingRepository.findByShortUrl("myalias");

        assertNotNull(found);
        assertEquals("myalias", found.getCustomAlias());
        assertEquals("https://youtube.com", found.getOriginalUrl());
    }

    //
    // TEST 6: two different users — each only gets their own URLs
    //
    @Test
    void findByUser_doesNotReturnOtherUsersUrls() {
        // second user
        User otherUser = new User();
        otherUser.setUsername("otheruser");
        otherUser.setEmail("other@test.com");
        otherUser.setPassword("password123");
        userRepository.save(otherUser);

        // URL for testUser
        UrlMapping mapping1 = new UrlMapping();
        mapping1.setOriginalUrl("https://google.com");
        mapping1.setShortUrl("aaa111");
        mapping1.setCreatedDate(LocalDateTime.now());
        mapping1.setUser(testUser);
        urlMappingRepository.save(mapping1);

        // URL for otherUser
        UrlMapping mapping2 = new UrlMapping();
        mapping2.setOriginalUrl("https://github.com");
        mapping2.setShortUrl("bbb222");
        mapping2.setCreatedDate(LocalDateTime.now());
        mapping2.setUser(otherUser);
        urlMappingRepository.save(mapping2);

        List<UrlMapping> testUserUrls = urlMappingRepository.findByUser(testUser);
        List<UrlMapping> otherUserUrls = urlMappingRepository.findByUser(otherUser);

        assertEquals(1, testUserUrls.size());
        assertEquals(1, otherUserUrls.size());
        assertEquals("aaa111", testUserUrls.get(0).getShortUrl());
        assertEquals("bbb222", otherUserUrls.get(0).getShortUrl());
    }
}
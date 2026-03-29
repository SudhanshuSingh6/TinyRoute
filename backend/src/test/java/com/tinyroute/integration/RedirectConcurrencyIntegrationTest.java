package com.tinyroute.integration;

import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.repository.analytics.UrlUniqueVisitorRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.repository.user.UserRepository;
import com.tinyroute.service.analytics.AsyncAnalyticsWorker;
import com.tinyroute.service.redirect.UrlRedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ActiveProfiles("test")
class RedirectConcurrencyIntegrationTest {

    @Autowired
    private UrlRedirectService urlRedirectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private UrlUniqueVisitorRepository urlUniqueVisitorRepository;

    @MockitoBean
    private AsyncAnalyticsWorker asyncAnalyticsWorker;

    @BeforeEach
    void setUp() {
        urlUniqueVisitorRepository.deleteAll();
        urlMappingRepository.deleteAll();
        userRepository.deleteAll();

        doNothing().when(asyncAnalyticsWorker).recordClickEvent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void redirectConcurrency_respectsMaxClicksUnderRace() throws Exception {
        createActiveMapping("race-max", 1);

        runConcurrentRedirectCalls("race-max", 8);

        MockHttpServletRequest followUpRequest = new MockHttpServletRequest();
        followUpRequest.setRemoteAddr("9.9.9.9");
        followUpRequest.addHeader("User-Agent", "Mozilla/5.0");
        followUpRequest.addHeader("Referer", "https://example.com");
        followUpRequest.addHeader("Accept-Language", "en-US,en;q=0.9");

        urlRedirectService.getOriginalUrl("race-max", followUpRequest);

        UrlMapping updated = urlMappingRepository.findByShortUrl("race-max");

        assertEquals(1, updated.getClickCount());
        assertEquals(UrlStatus.CLICK_LIMIT_REACHED, updated.getStatus());
        assertEquals(1, urlUniqueVisitorRepository.count());
    }

    @Test
    void redirectConcurrency_marksSingleUniqueClickForSameIp() throws Exception {
        createActiveMapping("race-uniq", 100);

        runConcurrentRedirectCalls("race-uniq", 12);

        UrlMapping updated = urlMappingRepository.findByShortUrl("race-uniq");

        assertEquals(1, updated.getClickCount());
        assertEquals(1, urlUniqueVisitorRepository.count());
        assertEquals(UrlStatus.ACTIVE, updated.getStatus());
    }

    private UrlMapping createActiveMapping(String shortUrl, Integer maxClicks) {
        User user = new User();
        user.setUsername("concurrency_user_" + shortUrl);
        user.setEmail(shortUrl + "@example.com");
        user.setPassword("password123");
        user.setRole(Role.ROLE_USER);
        user = userRepository.save(user);

        UrlMapping mapping = new UrlMapping();
        mapping.setUser(user);
        mapping.setOriginalUrl("https://openai.com/" + shortUrl);
        mapping.setShortUrl(shortUrl);
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setMaxClicks(maxClicks);
        mapping.setCreatedDate(LocalDateTime.now());
        mapping.setClickCount(0);

        return urlMappingRepository.save(mapping);
    }

    private void runConcurrentRedirectCalls(String shortUrl, int threadCount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();

                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.setRemoteAddr("9.9.9.9");
                    request.addHeader("User-Agent", "Mozilla/5.0");
                    request.addHeader("Referer", "https://example.com");
                    request.addHeader("Accept-Language", "en-US,en;q=0.9");

                    urlRedirectService.getOriginalUrl(shortUrl, request);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        if (!failures.isEmpty()) {
            System.out.println("==== THREAD FAILURES ====");
            failures.forEach(Throwable::printStackTrace);
        }
        assertTrue(failures.isEmpty(), "No thread should fail during concurrent redirect calls");

    }
}
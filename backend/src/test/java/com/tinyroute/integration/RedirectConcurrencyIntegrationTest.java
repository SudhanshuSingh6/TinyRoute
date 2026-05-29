package com.tinyroute.integration;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import com.tinyroute.user.entity.Role;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.user.entity.User;
import com.tinyroute.analytics.repository.UrlUniqueVisitorRepository;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.user.repository.UserRepository;
import com.tinyroute.analytics.service.UniqueVisitorRegistrationService;
import com.tinyroute.redirect.service.RedirectService;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RedirectConcurrencyIntegrationTest {

    @Autowired
    private RedirectService redirectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private UrlUniqueVisitorRepository urlUniqueVisitorRepository;

    @MockitoBean
    private UniqueVisitorRegistrationService uniqueVisitorRegistrationService;
    @MockitoBean
    private RedisClient redisClient;
    @MockitoBean
    private StatefulRedisConnection<String, byte[]> redisConnection;
    @MockitoBean
    private ProxyManager<String> proxyManager;

    @BeforeEach
    void setUp() {
        urlUniqueVisitorRepository.deleteAll();
        urlMappingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void redirectConcurrency_respectsMaxClicksUnderRace() throws Exception {
        // A link already at its click limit must resolve to CLICK_LIMIT_REACHED for every
        // concurrent request — the status guard must be race-safe. clickCount itself is
        // incremented asynchronously by the analytics worker, so it is not asserted here.
        UrlMapping mapping = createActiveMapping("race-max", 1);
        mapping.setClickCount(1); // already at the limit
        urlMappingRepository.save(mapping);

        runConcurrentRedirectCalls("race-max", 8);

        UrlMapping updated = urlMappingRepository.findByShortUrl("race-max");
        assertEquals(UrlStatus.CLICK_LIMIT_REACHED, updated.getStatus());
        assertEquals(1, updated.getClickCount());
    }

    @Test
    void redirectConcurrency_underLimitStaysActiveAndIsRaceSafe() throws Exception {
        // Concurrent same-IP hits on an under-limit link must all be served safely and leave
        // the link ACTIVE. Unique-visitor de-duplication and click counting are performed
        // asynchronously by the worker (UniqueVisitorRegistrationService + the DB unique
        // constraint covered in PersistenceRulesDataJpaTest), so clickCount stays 0 here.
        createActiveMapping("race-uniq", 100);

        runConcurrentRedirectCalls("race-uniq", 12);

        UrlMapping updated = urlMappingRepository.findByShortUrl("race-uniq");
        assertEquals(UrlStatus.ACTIVE, updated.getStatus());
        assertEquals(0, updated.getClickCount());
    }

    private void stubFirstVisitOnly() {
        AtomicBoolean first = new AtomicBoolean(true);
        when(uniqueVisitorRegistrationService.registerIfFirstVisit(anyLong(), anyString(), any()))
                .thenAnswer(invocation -> first.getAndSet(false));
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
        mapping.setCreatedAt(LocalDateTime.now());
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

                    redirectService.getOriginalUrl(shortUrl, request);
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

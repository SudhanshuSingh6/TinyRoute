package com.tinyroute.repository;

import com.tinyroute.entity.ClickEvent;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlEditHistory;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.UrlUniqueVisitor;
import com.tinyroute.entity.User;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.analytics.UrlUniqueVisitorRepository;
import com.tinyroute.repository.url.UrlEditHistoryRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PersistenceRulesDataJpaTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UrlMappingRepository urlMappingRepository;
    @Autowired
    private ClickEventRepository clickEventRepository;
    @Autowired
    private UrlEditHistoryRepository urlEditHistoryRepository;
    @Autowired
    private UrlUniqueVisitorRepository urlUniqueVisitorRepository;

    private UrlMapping urlMapping;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("repo_user");
        user.setEmail("repo_user@example.com");
        user.setPassword("password123");
        user.setRole(Role.ROLE_USER);
        user = userRepository.save(user);

        UrlMapping mapping = new UrlMapping();
        mapping.setUser(user);
        mapping.setOriginalUrl("https://openai.com");
        mapping.setShortUrl("repo1234");
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setCreatedDate(LocalDateTime.now());
        this.urlMapping = urlMappingRepository.save(mapping);
    }

    @Test
    void urlUniqueVisitor_enforcesUniqueUrlAndIp() {
        UrlUniqueVisitor first = new UrlUniqueVisitor();
        first.setUrlMapping(urlMapping);
        first.setIpHash("ip-hash-1");
        first.setFirstSeenAt(LocalDateTime.now());
        urlUniqueVisitorRepository.saveAndFlush(first);

        UrlUniqueVisitor duplicate = new UrlUniqueVisitor();
        duplicate.setUrlMapping(urlMapping);
        duplicate.setIpHash("ip-hash-1");
        duplicate.setFirstSeenAt(LocalDateTime.now());

        assertThrows(
                DataIntegrityViolationException.class,
                () -> urlUniqueVisitorRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    void incrementClickCount_updatesCountAndTimestamp() {
        LocalDateTime before = LocalDateTime.now();
        int updated = urlMappingRepository.incrementClickCount(urlMapping.getId());
        
        assertEquals(1, updated);
        
        UrlMapping refreshed = urlMappingRepository.findById(urlMapping.getId()).orElseThrow();
        assertEquals(1, refreshed.getClickCount());
        assertEquals(before, refreshed.getLastClickedAt());
    }

    @Test
    void deleteByUrlMapping_methodsRemoveDependentRows() {
        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setUrlMapping(urlMapping);
        clickEvent.setClickDate(LocalDateTime.now());
        clickEvent.setIpHash("hash-2");
        clickEventRepository.saveAndFlush(clickEvent);

        UrlEditHistory history = new UrlEditHistory();
        history.setUrlMapping(urlMapping);
        history.setOldUrl("https://legacy.openai.com");
        history.setChangedAt(LocalDateTime.now());
        urlEditHistoryRepository.saveAndFlush(history);

        UrlUniqueVisitor uniqueVisitor = new UrlUniqueVisitor();
        uniqueVisitor.setUrlMapping(urlMapping);
        uniqueVisitor.setIpHash("hash-3");
        uniqueVisitor.setFirstSeenAt(LocalDateTime.now());
        urlUniqueVisitorRepository.saveAndFlush(uniqueVisitor);

        assertEquals(1, clickEventRepository.count());
        assertEquals(1, urlEditHistoryRepository.count());
        assertEquals(1, urlUniqueVisitorRepository.count());

        clickEventRepository.deleteByUrlMapping(urlMapping);
        urlEditHistoryRepository.deleteByUrlMapping(urlMapping);
        urlUniqueVisitorRepository.deleteByUrlMapping(urlMapping);

        assertEquals(0, clickEventRepository.count());
        assertEquals(0, urlEditHistoryRepository.count());
        assertEquals(0, urlUniqueVisitorRepository.count());
    }
}

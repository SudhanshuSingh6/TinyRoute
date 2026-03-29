package com.tinyroute.integration;

import com.tinyroute.entity.ClickEvent;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.infra.geo.GeoLocationService;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.infra.ua.UserAgentParsingService;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.analytics.UrlUniqueVisitorRepository;
import com.tinyroute.repository.url.UrlEditHistoryRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RedirectFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @MockitoBean
    private ClientIpService clientIpService;
    @MockitoBean
    private GeoLocationService geoLocationService;
    @MockitoBean
    private UserAgentParsingService userAgentParsingService;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        urlUniqueVisitorRepository.deleteAll();
        urlEditHistoryRepository.deleteAll();
        urlMappingRepository.deleteAll();
        userRepository.deleteAll();

        when(clientIpService.resolveClientIp(any())).thenReturn("1.1.1.1");
        when(clientIpService.hashIp("1.1.1.1")).thenReturn("hash-111");
        when(geoLocationService.lookup("1.1.1.1"))
                .thenReturn(new GeoLocationService.GeoLocation("India", "Bengaluru"));
        when(userAgentParsingService.parse(any()))
                .thenReturn(new UserAgentParsingService.ParsedUserAgent("Chrome", "Windows", "Desktop"));
    }

    @Test
    void redirectEndpoint_activeLink_returns302AndPersistsClickData() throws Exception {
        User user = new User();
        user.setUsername("integration_user");
        user.setEmail("integration_user@example.com");
        user.setPassword("password123");
        user.setRole(Role.ROLE_USER);
        user = userRepository.save(user);

        UrlMapping mapping = new UrlMapping();
        mapping.setUser(user);
        mapping.setOriginalUrl("https://openai.com");
        mapping.setShortUrl("it123456");
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setCreatedDate(LocalDateTime.now());
        urlMappingRepository.save(mapping);

        mockMvc.perform(get("/it123456")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://example.com")
                        .header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://openai.com"));

        UrlMapping updated = urlMappingRepository.findByShortUrl("it123456");
        assertNotNull(updated);
        assertEquals(1, updated.getClickCount());
        assertNotNull(updated.getLastClickedAt());

        List<ClickEvent> clickEvents = clickEventRepository.findByUrlMappingAndClickDateBetween(
                updated,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        assertEquals(1, clickEvents.size());
        assertEquals(1, urlUniqueVisitorRepository.count());
    }
}

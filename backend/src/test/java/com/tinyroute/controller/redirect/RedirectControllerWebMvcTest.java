package com.tinyroute.controller.redirect;

import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.service.redirect.UrlRedirectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RedirectController.class)
@AutoConfigureMockMvc(addFilters = false)
class RedirectControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlRedirectService urlRedirectService;

    @Test
    void redirect_activeLink_returns302WithLocation() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setOriginalUrl("https://openai.com");
        when(urlRedirectService.getOriginalUrl(eq("abc12345"), any())).thenReturn(mapping);

        mockMvc.perform(get("/abc12345"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://openai.com"));
    }

    @Test
    void redirect_whenMissingLink_returns404() throws Exception {
        when(urlRedirectService.getOriginalUrl(eq("missing"), any())).thenReturn(null);

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirect_expiredLink_returns410() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setStatus(UrlStatus.EXPIRED);
        mapping.setExpiresAt(LocalDateTime.of(2026, 3, 1, 0, 0));
        when(urlRedirectService.getOriginalUrl(eq("expired1"), any())).thenReturn(mapping);

        mockMvc.perform(get("/expired1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void redirect_invalidDestinationScheme_returns410() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setOriginalUrl("javascript:alert(1)");
        when(urlRedirectService.getOriginalUrl(eq("badlink"), any())).thenReturn(mapping);

        mockMvc.perform(get("/badlink"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("This link has an invalid destination URL."));
    }
}

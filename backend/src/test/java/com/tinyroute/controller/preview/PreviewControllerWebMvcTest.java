package com.tinyroute.controller.preview;

import com.tinyroute.dto.url.response.UrlPreviewResponse;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.exception.UrlException;
import com.tinyroute.service.url.UrlLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PreviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PreviewControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlLookupService urlLookupService;

    @Test
    void getPreview_validShortUrl_returns200AndPreviewData() throws Exception {
        UrlPreviewResponse mockResponse = new UrlPreviewResponse();
        mockResponse.setTitle("OpenAI");
        mockResponse.setDescription("Artificial Intelligence");
        mockResponse.setImageUrl("https://openai.com/logo.png");
        mockResponse.setOriginalUrl("https://openai.com");

        when(urlLookupService.getPreview(eq("abc12345"))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/urls/abc12345/preview")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("OpenAI"))
                .andExpect(jsonPath("$.description").value("Artificial Intelligence"))
                .andExpect(jsonPath("$.imageUrl").value("https://openai.com/logo.png"))
                .andExpect(jsonPath("$.originalUrl").value("https://openai.com"));
    }

    @Test
    void getPreview_invalidOrMissingShortUrl_returns404() throws Exception {
        when(urlLookupService.getPreview(eq("missing")))
                .thenThrow(UrlException.notFound());

        mockMvc.perform(get("/api/urls/missing/preview")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}

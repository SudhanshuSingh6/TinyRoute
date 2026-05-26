package com.tinyroute.controller.qr;

import com.tinyroute.exception.handler.GlobalExceptionHandler;
import com.tinyroute.exception.UrlException;
import com.tinyroute.url.controller.QrCodeController;
import com.tinyroute.url.service.UrlPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QrCodeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class QrCodeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlPreviewService urlPreviewService;

    @Test
    void getQrCode_validShortUrl_returns200AndPngImage() throws Exception {
        byte[] mockPng = new byte[]{1, 2, 3, 4, 5};
        when(urlPreviewService.generateQr(eq("abc12345"), anyString())).thenReturn(mockPng);

        mockMvc.perform(get("/api/urls/abc12345/qr"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(mockPng));
    }

    @Test
    void getQrCode_invalidOrMissingShortUrl_returns404() throws Exception {
        when(urlPreviewService.generateQr(eq("missing"), anyString())).thenReturn(null);

        mockMvc.perform(get("/api/urls/missing/qr"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getQrCode_urlLookupServiceThrowsNotFound_returns404() throws Exception {
        when(urlPreviewService.generateQr(eq("inactive"), anyString()))
                .thenThrow(UrlException.notFound());

        mockMvc.perform(get("/api/urls/inactive/qr"))
                .andExpect(status().isNotFound());
    }
}

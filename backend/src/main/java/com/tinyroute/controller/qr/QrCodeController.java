package com.tinyroute.controller.qr;

import com.tinyroute.service.url.UrlLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QR", description = "Public URL QR code endpoint")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class QrCodeController {

    private final UrlLookupService urlLookupService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Operation(
            summary = "Generate QR code for a short URL",
            description = "Returns a 250x250 PNG QR code encoding the full short URL. Public endpoint — no auth required."
    )
    @ApiResponse(responseCode = "200", description = "PNG image returned")
    @ApiResponse(responseCode = "404", description = "Short URL not found")
    @GetMapping("/{shortUrl}/qr")
    public ResponseEntity<byte[]> getQrCode(
            @Parameter(description = "The short URL code", example = "abc12345")
            @PathVariable String shortUrl) {
        String baseUrl = frontendUrl.replaceAll("/$", "");
        byte[] png = urlLookupService.generateQr(shortUrl, baseUrl);
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}

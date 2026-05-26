package com.tinyroute.url.controller;

import com.tinyroute.url.dto.UrlPreviewResponse;
import com.tinyroute.url.service.UrlPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Preview", description = "Public URL preview endpoints")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class PreviewController {

    private final UrlPreviewService urlPreviewService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Operation(summary = "Preview a short URL destination", description = "Scrapes the destination page for title, description and og:image. Public endpoint — no authentication required.")
    @ApiResponse(responseCode = "200", description = "Preview data returned")
    @ApiResponse(responseCode = "404", description = "Short URL not found")
    @GetMapping("/{shortUrl}/preview")
    public ResponseEntity<UrlPreviewResponse> getPreview(
            @Parameter(description = "Short URL code", example = "abc12345")
            @PathVariable String shortUrl) {

        UrlPreviewResponse preview = urlPreviewService.getPreview(shortUrl);

        return ResponseEntity.ok(preview);
    }

    @Operation(summary = "Generate QR code for a short URL", description = "Returns a 250x250 PNG QR code encoding the full short URL. Public endpoint — no authentication required.")
    @ApiResponse(responseCode = "200", description = "PNG image returned")
    @ApiResponse(responseCode = "404", description = "Short URL not found")
    @GetMapping("/{shortUrl}/qr")
    public ResponseEntity<byte[]> getQrCode(
            @Parameter(description = "Short URL code", example = "abc12345")
            @PathVariable String shortUrl) {

        String baseUrl = frontendUrl.replaceAll("/$", "");

        byte[] png = urlPreviewService.generateQr(shortUrl, baseUrl);

        if (png == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
package com.tinyroute.controller.preview;

import com.tinyroute.dto.url.response.UrlPreviewResponse;
import com.tinyroute.service.url.UrlLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Preview", description = "Public URL preview endpoint")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class PreviewController {

    private final UrlLookupService urlLookupService;

    @Operation(
            summary = "Preview a short URL's destination",
            description = "Scrapes the destination page for title, description and og:image. Public endpoint — no auth required."
    )
    @ApiResponse(responseCode = "200", description = "Preview data returned")
    @ApiResponse(responseCode = "404", description = "Short URL not found")
    @GetMapping("/{shortUrl}/preview")
    public ResponseEntity<?> getPreview(
            @Parameter(description = "The short URL code", example = "abc12345")
            @PathVariable String shortUrl) {
        UrlPreviewResponse preview = urlLookupService.getPreview(shortUrl);
        if (preview == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(preview);
    }
}

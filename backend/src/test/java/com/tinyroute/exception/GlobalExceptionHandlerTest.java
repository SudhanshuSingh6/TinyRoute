package com.tinyroute.exception;

import com.tinyroute.exception.handler.GlobalExceptionHandler;
import com.tinyroute.exception.response.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDateParse_returnsBadRequestWithExpectedErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/urls/analytics/abc12345");

        DateTimeParseException ex = new DateTimeParseException("bad date", "not-a-date", 0);
        ResponseEntity<ApiErrorResponse> response = handler.handleDateParse(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_DATE_FORMAT", response.getBody().getError());
        assertEquals("/api/urls/analytics/abc12345", response.getBody().getPath());
    }

    @Test
    void handleBadCredentials_returnsUnauthorizedWithErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/public/login");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadCredentials(
                new BadCredentialsException("bad"),
                request
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_CREDENTIALS", response.getBody().getError());
    }

    @Test
    void handleUsernameNotFound_returnsNotFoundWithErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/profile");

        ResponseEntity<ApiErrorResponse> response = handler.handleUsernameNotFound(
                new UsernameNotFoundException("alice"),
                request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("USER_NOT_FOUND", response.getBody().getError());
    }

    @Test
    void handleIllegalArgument_returnsBadRequestWithErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/x");

        ResponseEntity<ApiErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("bad input"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_ARGUMENT", response.getBody().getError());
    }

    @Test
    void handleMissingRequestParameter_returnsBadRequestWithErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/urls/totalClicks");

        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("startDate", "String");
        ResponseEntity<ApiErrorResponse> response = handler.handleMissingRequestParameter(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MISSING_PARAMETER", response.getBody().getError());
    }


    @Test
    void handleTypeMismatch_returnsBadRequestWithParameterName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/urls/totalClicks");

        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "bad-date",
                LocalDate.class,
                "startDate",
                null,
                new IllegalArgumentException("invalid format")
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_PARAMETER_TYPE", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("startDate"));
    }

    @Test
    void handleApiException_plainException_mapsStatusCodeAndPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/urls/xyz");

        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, "URL_NOT_FOUND", "nope");
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("URL_NOT_FOUND", response.getBody().getError());
        assertEquals("nope", response.getBody().getMessage());
        assertEquals("/api/urls/xyz", response.getBody().getPath());
    }

    @Test
    void handleApiException_rateLimitException_setsRetryAfterHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/abc");

        RateLimitExceededException ex = new RateLimitExceededException("too many", 30L);
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().getError());
        assertEquals("30", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void handleValidation_fieldErrors_returnsBadRequestWithFieldMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/urls/shorten");

        BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "originalUrl", "must not be blank"));

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(bindException, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("originalUrl"));
    }

    @Test
    void handleMalformedBody_returnsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/urls/shorten");

        @SuppressWarnings("deprecation")
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json");
        ResponseEntity<ApiErrorResponse> response = handler.handleMalformedBody(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MALFORMED_REQUEST_BODY", response.getBody().getError());
    }

    @Test
    void handleAuthentication_returnsUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/profile");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthentication(
                new InsufficientAuthenticationException("no auth"), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("AUTHENTICATION_FAILED", response.getBody().getError());
    }

    @Test
    void handleGeneric_returnsInternalServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/x");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleGeneric(new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getError());
    }
}

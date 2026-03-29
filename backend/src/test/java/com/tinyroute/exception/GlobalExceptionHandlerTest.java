package com.tinyroute.exception;

import com.tinyroute.dto.error.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
}

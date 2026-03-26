package com.tinyroute.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Optional;

@Getter
public class RateLimitExceededException extends ApiException {

    private static final HttpStatus STATUS = HttpStatus.TOO_MANY_REQUESTS;
    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    private final Optional<Long> retryAfterSeconds;

    public RateLimitExceededException(String message) {
        super(STATUS, ERROR_CODE, message);
        this.retryAfterSeconds = Optional.empty();
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(STATUS, ERROR_CODE, message);
        this.retryAfterSeconds = Optional.of(retryAfterSeconds);
    }

}
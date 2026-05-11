package com.tinyroute.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Optional;

@Getter
public class RateLimitExceededException extends ApiException {

    private final Optional<Long> retryAfterSeconds;

    public RateLimitExceededException(String message) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCodes.RATE_LIMIT_EXCEEDED,
                message
        );
        this.retryAfterSeconds = Optional.empty();
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCodes.RATE_LIMIT_EXCEEDED,
                message
        );
        this.retryAfterSeconds = Optional.of(retryAfterSeconds);
    }
}
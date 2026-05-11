package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class ShortUrlGenerationFailedException extends ApiException {

    public ShortUrlGenerationFailedException() {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.SHORT_CODE_EXHAUSTED,
                ErrorMessages.SHORT_CODE_EXHAUSTED
        );
    }

    public ShortUrlGenerationFailedException(String message) {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.SHORT_CODE_EXHAUSTED,
                message
        );
    }
}
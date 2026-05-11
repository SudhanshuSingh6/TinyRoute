package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class InvalidUrlException extends ApiException {

    public InvalidUrlException(String message) {
        super(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.INVALID_URL,
                message
        );
    }
}
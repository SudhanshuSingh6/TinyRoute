package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class InvalidDestinationUrlException extends ApiException {

    public InvalidDestinationUrlException() {
        super(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.INVALID_DESTINATION_URL,
                ErrorMessages.INVALID_DESTINATION_URL
        );
    }

    public InvalidDestinationUrlException(String message) {
        super(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.INVALID_DESTINATION_URL,
                message
        );
    }
}
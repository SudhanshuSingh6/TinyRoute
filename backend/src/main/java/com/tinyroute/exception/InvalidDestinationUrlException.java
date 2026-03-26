package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class InvalidDestinationUrlException extends ApiException {

    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
    private static final String ERROR_CODE = "INVALID_DESTINATION_URL";

    public InvalidDestinationUrlException(String message) {
        super(STATUS, ERROR_CODE, message);
    }

}
package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class InvalidUrlException extends ApiException {

    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
    private static final String ERROR_CODE = "INVALID_URL";

    public InvalidUrlException(String message) {
        super(STATUS, ERROR_CODE, message);
    }

}
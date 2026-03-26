package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends ApiException {

    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    private static final String ERROR_CODE = "USERNAME_ALREADY_EXISTS";

    public UsernameAlreadyExistsException(String message) {
        super(STATUS, ERROR_CODE, message);
    }

}
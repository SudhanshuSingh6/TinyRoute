package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class DuplicateAliasException extends ApiException {

    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    private static final String ERROR_CODE = "DUPLICATE_ALIAS";

    public DuplicateAliasException(String message) {
        super(STATUS, ERROR_CODE, message);
    }

}
package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class DomainBlacklistedException extends ApiException {

    private static final HttpStatus STATUS = HttpStatus.UNPROCESSABLE_ENTITY;
    private static final String ERROR_CODE = "DOMAIN_BLACKLISTED";

    public DomainBlacklistedException(String message) {
        super(STATUS, ERROR_CODE, message);
    }
}
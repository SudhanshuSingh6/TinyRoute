package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class DomainBlacklistedException extends ApiException {

    public DomainBlacklistedException() {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCodes.DOMAIN_BLACKLISTED,
                ErrorMessages.DOMAIN_BLACKLISTED
        );
    }

    public DomainBlacklistedException(String message) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCodes.DOMAIN_BLACKLISTED,
                message
        );
    }
}
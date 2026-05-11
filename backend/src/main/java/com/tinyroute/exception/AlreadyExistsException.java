package com.tinyroute.exception;

import org.springframework.http.HttpStatus;

public class AlreadyExistsException extends ApiException {

    private AlreadyExistsException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }

    public static AlreadyExistsException email() {
        return new AlreadyExistsException(
                ErrorCodes.EMAIL_ALREADY_EXISTS,
                ErrorMessages.EMAIL_ALREADY_EXISTS
        );
    }

    public static AlreadyExistsException username() {
        return new AlreadyExistsException(
                ErrorCodes.USERNAME_ALREADY_EXISTS,
                ErrorMessages.USERNAME_ALREADY_EXISTS
        );
    }

    public static AlreadyExistsException alias() {
        return new AlreadyExistsException(
                ErrorCodes.DUPLICATE_ALIAS,
                ErrorMessages.DUPLICATE_ALIAS
        );
    }

    public static AlreadyExistsException custom(String errorCode, String message) {
        return new AlreadyExistsException(errorCode, message);
    }
}
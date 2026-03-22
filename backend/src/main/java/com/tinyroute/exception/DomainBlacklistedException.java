package com.tinyroute.exception;

public class DomainBlacklistedException extends RuntimeException {
    public DomainBlacklistedException(String message) {
        super(message);
    }
}
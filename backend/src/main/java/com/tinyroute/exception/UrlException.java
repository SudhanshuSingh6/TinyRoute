package com.tinyroute.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UrlException extends ApiException {

    public enum Reason {
        NOT_FOUND,
        ACCESS_DENIED,
        EXPIRED,
        DISABLED
    }

    private final Reason reason;

    private UrlException(Reason reason, String message) {
        super(resolveStatus(reason), resolveErrorCode(reason), message);
        this.reason = reason;
    }

    private UrlException(Reason reason, String message, Throwable cause) {
        super(resolveStatus(reason), resolveErrorCode(reason), message, cause);
        this.reason = reason;
    }

    public static UrlException notFound() {
        return new UrlException(Reason.NOT_FOUND, ErrorMessages.URL_NOT_FOUND);
    }

    public static UrlException notFound(String message) {
        return new UrlException(Reason.NOT_FOUND, message);
    }

    public static UrlException accessDenied() {
        return new UrlException(Reason.ACCESS_DENIED, ErrorMessages.URL_ACCESS_DENIED);
    }

    public static UrlException accessDenied(String message) {
        return new UrlException(Reason.ACCESS_DENIED, message);
    }

    public static UrlException expired() {
        return new UrlException(Reason.EXPIRED, ErrorMessages.URL_EXPIRED);
    }

    public static UrlException expired(String message) {
        return new UrlException(Reason.EXPIRED, message);
    }

    public static UrlException disabled() {
        return new UrlException(Reason.DISABLED, ErrorMessages.URL_DISABLED);
    }

    public static UrlException disabled(String message) {
        return new UrlException(Reason.DISABLED, message);
    }

    private static HttpStatus resolveStatus(Reason reason) {
        return switch (reason) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case EXPIRED, DISABLED -> HttpStatus.GONE;
        };
    }

    private static String resolveErrorCode(Reason reason) {
        return switch (reason) {
            case NOT_FOUND -> ErrorCodes.URL_NOT_FOUND;
            case ACCESS_DENIED -> ErrorCodes.URL_ACCESS_DENIED;
            case EXPIRED -> ErrorCodes.URL_EXPIRED;
            case DISABLED -> ErrorCodes.URL_DISABLED;
        };
    }
}
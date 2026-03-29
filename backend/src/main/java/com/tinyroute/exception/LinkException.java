package com.tinyroute.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LinkException extends ApiException {

    public enum Reason {
        NOT_FOUND,
        ACCESS_DENIED,
        EXPIRED,
        DISABLED
    }
    private final Reason reason;

    public LinkException(Reason reason, String message) {
        super(resolveStatus(reason), resolveErrorCode(reason), message);
        this.reason = reason;
    }

    public LinkException(Reason reason, String message, Throwable cause) {
        super(resolveStatus(reason), resolveErrorCode(reason), message, cause);
        this.reason = reason;
    }

    public static LinkException notFound(String message) {
        return new LinkException(Reason.NOT_FOUND, message);
    }

    public static LinkException accessDenied(String message) {
        return new LinkException(Reason.ACCESS_DENIED, message);
    }

    public static LinkException expired(String message) {
        return new LinkException(Reason.EXPIRED, message);
    }

    public static LinkException disabled(String message) {
        return new LinkException(Reason.DISABLED, message);
    }

    public static LinkException notFound(String message, Throwable cause) {
        return new LinkException(Reason.NOT_FOUND, message, cause);
    }

    public static LinkException accessDenied(String message, Throwable cause) {
        return new LinkException(Reason.ACCESS_DENIED, message, cause);
    }

    public static LinkException expired(String message, Throwable cause) {
        return new LinkException(Reason.EXPIRED, message, cause);
    }

    public static LinkException disabled(String message, Throwable cause) {
        return new LinkException(Reason.DISABLED, message, cause);
    }

    private static HttpStatus resolveStatus(Reason reason) {
        return switch (reason) {
            case NOT_FOUND    -> HttpStatus.NOT_FOUND;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case EXPIRED, DISABLED -> HttpStatus.GONE;
        };
    }

    private static String resolveErrorCode(Reason reason) {
        return switch (reason) {
            case NOT_FOUND    -> "LINK_NOT_FOUND";
            case ACCESS_DENIED -> "LINK_ACCESS_DENIED";
            case EXPIRED      -> "LINK_EXPIRED";
            case DISABLED     -> "LINK_DISABLED";
        };
    }
}
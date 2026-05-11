package com.tinyroute.exception;

public final class ErrorCodes {

    private ErrorCodes() {}

    public static final String DOMAIN_BLACKLISTED = "DOMAIN_BLACKLISTED";
    public static final String DUPLICATE_ALIAS = "DUPLICATE_ALIAS";
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    public static final String INVALID_DESTINATION_URL = "INVALID_DESTINATION_URL";
    public static final String INVALID_URL = "INVALID_URL";
    public static final String URL_ALREADY_EXISTS = "URL_ALREADY_EXISTS";
    public static final String URL_NOT_FOUND = "URL_NOT_FOUND";
    public static final String URL_ACCESS_DENIED = "URL_ACCESS_DENIED";
    public static final String URL_EXPIRED = "URL_EXPIRED";
    public static final String URL_DISABLED = "URL_DISABLED";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String SHORT_CODE_EXHAUSTED = "SHORT_CODE_EXHAUSTED";
    public static final String USERNAME_ALREADY_EXISTS = "USERNAME_ALREADY_EXISTS";
    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    public static final String SHORT_URL_GENERATION_FAILED = "SHORT_URL_GENERATION_FAILED";
    public static final String URL_DISABLE_INVALID = "URL_DISABLE_INVALID";
    public static final String URL_ENABLE_INVALID = "URL_ENABLE_INVALID";
    public static final String CLICK_LIMIT_REACHED = "CLICK_LIMIT_REACHED";

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String MALFORMED_REQUEST_BODY = "MALFORMED_REQUEST_BODY";
    public static final String INVALID_DATE_FORMAT = "INVALID_DATE_FORMAT";
    public static final String INVALID_PARAMETER_TYPE = "INVALID_PARAMETER_TYPE";
    public static final String MISSING_PARAMETER = "MISSING_PARAMETER";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
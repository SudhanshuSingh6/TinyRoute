package com.tinyroute.exception;

public final class ErrorMessages {

    private ErrorMessages() {}

    public static final String URL_NOT_FOUND = "Short URL not found";
    public static final String URL_ACCESS_DENIED = "You do not have access to this URL";
    public static final String URL_EXPIRED = "This URL has expired";
    public static final String URL_DISABLED = "This URL is disabled";
    public static final String INVALID_DATE_RANGE = "EndDate must be after StartDate";
    public static final String URL_ALREADY_EXISTS = "A URL for this destination already exists. Use the existing URL instead.";
    public static final String URL_DISABLE_INVALID = "Only ACTIVE URLs can be disabled";
    public static final String URL_ENABLE_INVALID = "Only DISABLED URLs can be enabled";
    public static final String CLICK_LIMIT_REACHED = "Cannot enable a URL that has reached its click limit";

    public static final String USERNAME_ALREADY_EXISTS = "Username already exists";
    public static final String EMAIL_ALREADY_EXISTS = "Email already exists";
    public static final String DUPLICATE_ALIAS = "This alias is already taken";

    public static final String INVALID_URL = "Invalid URL";
    public static final String INVALID_DESTINATION_URL = "Invalid destination URL";
    public static final String DOMAIN_BLACKLISTED = "This domain is not allowed";
    public static final String SHORT_CODE_EXHAUSTED = "No short codes available right now";

    public static final String RATE_LIMIT_EXCEEDED = "Too many requests. Please try again later";

    public static final String INVALID_CREDENTIALS = "Username or password is incorrect";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String AUTHENTICATION_FAILED = "Authentication failed";
    public static final String INTERNAL_ERROR = "An unexpected error occurred";

    public static final String MALFORMED_REQUEST_BODY = "Request body is missing or malformed";
    public static final String INVALID_ARGUMENT = "Invalid argument";
    public static final String VALIDATION_ERROR = "Validation failed";
}
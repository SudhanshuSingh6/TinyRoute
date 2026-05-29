import toast from "react-hot-toast";

// Maps backend error codes (ApiErrorResponse.error) to user-friendly messages.
const ERROR_CODE_MESSAGES = {
  // Auth
  INVALID_CREDENTIALS:          "Incorrect username or password.",
  USER_NOT_FOUND:               "User not found.",
  AUTHENTICATION_FAILED:        "Authentication failed. Please log in again.",
  EMAIL_ALREADY_EXISTS:         "An account with this email already exists.",
  USERNAME_ALREADY_EXISTS:      "This username is already taken.",
  // Session / token rotation
  REFRESH_TOKEN_MISSING:        "Your session has expired. Please log in again.",
  INVALID_REFRESH_TOKEN:        "Your session is invalid. Please log in again.",
  REFRESH_TOKEN_REVOKED:        "Your session was revoked. Please log in again.",
  REFRESH_TOKEN_EXPIRED:        "Your session has expired. Please log in again.",
  // URL
  URL_NOT_FOUND:                "This link was not found.",
  URL_ACCESS_DENIED:            "You are not allowed to modify this link.",
  DUPLICATE_ALIAS:              "This custom alias is already taken.",
  DOMAIN_BLACKLISTED:           "This domain is not allowed.",
  INVALID_DESTINATION_URL:      "The destination URL is invalid.",
  INVALID_URL:                  "Please enter a valid URL.",
  SHORT_URL_GENERATION_FAILED:  "Could not generate a short URL. Please try again.",
  // Rate limiting
  RATE_LIMIT_EXCEEDED:          "Too many requests. Please try again shortly.",
  // Validation
  VALIDATION_ERROR:             "Please check your input and try again.",
  INVALID_DATE_RANGE:           "End date must be after start date.",
};

// Resolves the right toast message from a backend Axios error.
// `overrides` maps error codes to context-specific messages that take
// precedence over the shared map, e.g. { URL_ACCESS_DENIED: "You cannot delete this link." }
export function handleApiError(error, overrides = {}, fallback = "Something went wrong.") {
  const code = error?.response?.data?.error;
  const message =
    overrides[code] ??
    ERROR_CODE_MESSAGES[code] ??
    error?.response?.data?.message ??
    fallback;
  toast.error(message);
}

package com.higgstx.schwabapi.exception;

import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.util.HttpUtils;
import com.higgstx.schwabapi.util.JsonUtils;
import com.higgstx.schwabapi.util.UtilityClass;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Simplified exception for Schwab API errors with essential functionality only
 */
@Getter
public class SchwabApiException extends Exception {
    
    private final int statusCode;
    private final String errorCode;
    private final Map<String, Object> errorDetails;
    
    public SchwabApiException(int statusCode, String message, String errorCode, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode != null ? errorCode : "UNKNOWN_ERROR";
        this.errorDetails = details != null ? details : new HashMap<>();
    }
    
    /**
 * Severity enumeration for monitoring and alerting
 */
public enum Severity {
    DEBUG("Debug"),
    INFO("Info"), 
    WARNING("Warning"),
    ERROR("Error");
    
    private final String description;
    
    Severity(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}

/**
 * Get severity level based on status code
 */
public Severity getSeverity() {
    if (statusCode >= 500) {
        return Severity.ERROR;
    } else if (statusCode == 401 || statusCode == 403 || statusCode == 429) {
        return Severity.WARNING;
    } else if (statusCode >= 400) {
        return Severity.INFO;
    } else {
        return Severity.DEBUG;
    }
}

/**
 * Check if this error should trigger alerts (WARNING level and above)
 */
public boolean shouldAlert() {
    Severity severity = getSeverity();
    return severity == Severity.WARNING || severity == Severity.ERROR;
}
    
    /**
     * Get a user-friendly error message
     */
    public String getDisplayMessage() {
        return switch (getErrorCategory()) {
            case AUTHENTICATION -> "Authentication failed. Please check your credentials and try again.";
            case RATE_LIMIT -> "Too many requests. Please wait a moment and try again.";
            case CLIENT_ERROR -> "Invalid request. Please check your input and try again.";
            case SERVER_ERROR -> "Service temporarily unavailable. Please try again later.";
            case NETWORK_ERROR -> "Network connection error. Please check your internet connection.";
            default -> "An unexpected error occurred. Please try again or contact support.";
        };
    }
    
    /**
     * Create exception from API response
     */
    public static SchwabApiException fromApiResponse(String operation, ApiResponse response) {
        Map<String, Object> details = JsonUtils.extractErrorDetails(response.getBody(), UtilityClass.getObjectMapper());
        
        String errorCode = "HTTP_" + response.getStatusCode();
        if (details.containsKey("error")) {
            errorCode = details.get("error").toString();
        }
        
        String errorMessage = JsonUtils.extractErrorMessage(response.getBody(), UtilityClass.getObjectMapper());
        String message = UtilityClass.buildErrorMessage(operation, errorMessage);
        
        return new SchwabApiException(response.getStatusCode(), message, errorCode, details, null);
    }
    
    /**
     * Check if this error is retryable
     */
    public boolean isRetryable() {
        return HttpUtils.isRetryableStatusCode(statusCode);
    }
    
    /**
     * Check if this is an authentication/authorization error
     */
    public boolean isAuthError() {
        return HttpUtils.isAuthError(statusCode);
    }
    
    /**
     * Check if this is a rate limiting error
     */
    public boolean isRateLimited() {
        return statusCode == 429;
    }
    
    /**
     * Get retry after seconds (default values based on error type)
     */
    public long getRetryAfterSeconds() {
        if (isRateLimited()) {
            return 60; // Wait 1 minute for rate limits
        } else if (statusCode >= 500) {
            return 30; // Wait 30 seconds for server errors
        } else {
            return 10; // Wait 10 seconds for other retryable errors
        }
    }
    
    /**
     * Get error category for monitoring/alerting
     */
    public ErrorCategory getErrorCategory() {
        if (isAuthError()) {
            return ErrorCategory.AUTHENTICATION;
        } else if (isRateLimited()) {
            return ErrorCategory.RATE_LIMIT;
        } else if (HttpUtils.isServerError(statusCode)) {
            return ErrorCategory.SERVER_ERROR;
        } else if (HttpUtils.isClientError(statusCode)) {
            return ErrorCategory.CLIENT_ERROR;
        } else if (statusCode == 0) {
            return ErrorCategory.NETWORK_ERROR;
        } else {
            return ErrorCategory.UNKNOWN;
        }
    }
    
    /**
     * Error category enumeration
     */
    public enum ErrorCategory {
        AUTHENTICATION("Authentication/Authorization"),
        RATE_LIMIT("Rate Limiting"),
        CLIENT_ERROR("Client Error"),
        SERVER_ERROR("Server Error"),
        NETWORK_ERROR("Network Error"),
        UNKNOWN("Unknown Error");
        
        private final String description;
        
        ErrorCategory(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Get recommended action based on error type
     */
    public String getRecommendedAction() {
        return switch (getErrorCategory()) {
            case AUTHENTICATION -> "Check credentials and token validity. Re-authenticate if necessary.";
            case RATE_LIMIT -> "Reduce request frequency. Wait " + getRetryAfterSeconds() + " seconds before retrying.";
            case CLIENT_ERROR -> "Check request parameters and API documentation.";
            case SERVER_ERROR -> "Retry after " + getRetryAfterSeconds() + " seconds. Check Schwab API status.";
            case NETWORK_ERROR -> "Check network connectivity and DNS resolution.";
            default -> "Review error details and contact support if issue persists.";
        };
    }
    
    // Essential factory methods only
    public static SchwabApiException unauthorized(String message) {
        return new SchwabApiException(401, message, "UNAUTHORIZED", null, null);
    }
    
    public static SchwabApiException forbidden(String message) {
        return new SchwabApiException(403, message, "FORBIDDEN", null, null);
    }
    
    public static SchwabApiException notFound(String message) {
        return new SchwabApiException(404, message, "NOT_FOUND", null, null);
    }
    
    public static SchwabApiException serverError(String message) {
        return new SchwabApiException(500, message, "SERVER_ERROR", null, null);
    }
    
    public static SchwabApiException timeout(String message) {
        return new SchwabApiException(408, message, "TIMEOUT", null, null);
    }
    
    public static SchwabApiException networkError(String message, Throwable cause) {
        return new SchwabApiException(0, message, "NETWORK_ERROR", null, cause);
    }
    
    public static SchwabApiException configurationError(String message) {
        return new SchwabApiException(0, message, "CONFIGURATION_ERROR", null, null);
    }
    
    public static SchwabApiException tokenError(String message) {
        return new SchwabApiException(401, message, "TOKEN_ERROR", null, null);
    }
    
    public static SchwabApiException validationError(String message) {
        return new SchwabApiException(400, message, "VALIDATION_ERROR", null, null);
    }
    
    /**
     * Simple builder for complex cases
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int statusCode;
        private String message;
        private String errorCode;
        private Map<String, Object> details = new HashMap<>();
        private Throwable cause;
        
        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }
        
        public SchwabApiException build() {
            return new SchwabApiException(statusCode, message, errorCode, details, cause);
        }
    }
    
    @Override
    public String toString() {
        return String.format("SchwabApiException{statusCode=%d, errorCode='%s', category=%s, message='%s'}", 
                statusCode, errorCode, getErrorCategory(), getMessage());
    }
}
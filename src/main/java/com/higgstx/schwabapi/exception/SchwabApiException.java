package com.higgstx.schwabapi.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.higgstx.schwabapi.model.ApiResponse;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom exception for Schwab API errors with enhanced error handling and retry logic.
 */
@Getter
public class SchwabApiException extends Exception {
    
    private final int statusCode;
    private final String errorCode;
    private final Map<String, Object> errorDetails;
    private final long responseTime;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public SchwabApiException(int statusCode, String message, String errorCode, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode != null ? errorCode : "UNKNOWN_ERROR";
        this.errorDetails = details != null ? details : new HashMap<>();
        this.responseTime = 0;
    }
    
    public SchwabApiException(int statusCode, String message, String errorCode, Map<String, Object> details, long responseTime) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode != null ? errorCode : "UNKNOWN_ERROR";
        this.errorDetails = details != null ? details : new HashMap<>();
        this.responseTime = responseTime;
    }
    
    /**
     * Create exception from API response
     */
    public static SchwabApiException fromApiResponse(String message, ApiResponse response) {
        Map<String, Object> details = new HashMap<>();
        String errorCode = "HTTP_" + response.getStatusCode();
        
        try {
            // Try to parse error details from response body
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                JsonNode errorJson = objectMapper.readTree(response.getBody());
                
                if (errorJson.has("error")) {
                    errorCode = errorJson.get("error").asText();
                }
                
                if (errorJson.has("error_description")) {
                    details.put("description", errorJson.get("error_description").asText());
                }
                
                if (errorJson.has("message")) {
                    details.put("message", errorJson.get("message").asText());
                }
                
                if (errorJson.has("errors")) {
                    details.put("errors", errorJson.get("errors"));
                }
            }
        } catch (Exception e) {
            // If JSON parsing fails, store raw response
            details.put("raw_response", response.getBody());
        }
        
        // Add response metadata
        details.put("status_code", response.getStatusCode());
        details.put("response_time_ms", response.getResponseTimeMillis());
        details.put("headers", response.getHeaders());
        
        return new SchwabApiException(
            response.getStatusCode(),
            message + ": " + getErrorMessage(response),
            errorCode,
            details,
            response.getResponseTimeMillis()
        );
    }
    
    private static String getErrorMessage(ApiResponse response) {
        try {
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                JsonNode errorJson = objectMapper.readTree(response.getBody());
                
                if (errorJson.has("error_description")) {
                    return errorJson.get("error_description").asText();
                }
                
                if (errorJson.has("message")) {
                    return errorJson.get("message").asText();
                }
                
                if (errorJson.has("error")) {
                    return errorJson.get("error").asText();
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return "HTTP " + response.getStatusCode();
    }
    
    /**
     * Check if this error is retryable
     */
    public boolean isRetryable() {
        // Retry on server errors (5xx) or rate limiting (429)
        return statusCode >= 500 || statusCode == 429 || statusCode == 408; // Request timeout
    }
    
    /**
     * Check if this is an authentication/authorization error
     */
    public boolean isAuthError() {
        return statusCode == 401 || statusCode == 403;
    }
    
    /**
     * Check if this is a client error (4xx, but not auth or rate limit)
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500 && statusCode != 401 && statusCode != 403 && statusCode != 429;
    }
    
    /**
     * Check if this is a rate limiting error
     */
    public boolean isRateLimited() {
        return statusCode == 429;
    }
    
    /**
     * Get retry after seconds from headers or use default
     */
    public long getRetryAfterSeconds() {
        Object retryAfter = errorDetails.get("Retry-After");
        if (retryAfter instanceof String) {
            try {
                return Long.parseLong((String) retryAfter);
            } catch (NumberFormatException e) {
                // Try parsing as HTTP date - simplified approach
                return 60; // Default to 1 minute
            }
        }
        
        // Default retry times based on error type
        if (isRateLimited()) {
            return 60; // Wait 1 minute for rate limits
        } else if (statusCode >= 500) {
            return 30; // Wait 30 seconds for server errors
        } else {
            return 10; // Wait 10 seconds for other retryable errors
        }
    }
    
    /**
     * Get detailed error information
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Schwab API Error: ").append(getMessage());
        sb.append(" (Status: ").append(statusCode);
        sb.append(", Code: ").append(errorCode);
        sb.append(", Response Time: ").append(responseTime).append("ms)");
        
        if (errorDetails.containsKey("description")) {
            sb.append("\nDescription: ").append(errorDetails.get("description"));
        }
        
        if (errorDetails.containsKey("message")) {
            sb.append("\nAPI Message: ").append(errorDetails.get("message"));
        }
        
        return sb.toString();
    }
    
    /**
     * Get error category for monitoring/alerting
     */
    public ErrorCategory getErrorCategory() {
        if (isAuthError()) {
            return ErrorCategory.AUTHENTICATION;
        } else if (isRateLimited()) {
            return ErrorCategory.RATE_LIMIT;
        } else if (statusCode >= 500) {
            return ErrorCategory.SERVER_ERROR;
        } else if (statusCode >= 400) {
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
     * Get severity level for logging/monitoring
     */
    public Severity getSeverity() {
        if (statusCode >= 500) {
            return Severity.ERROR;
        } else if (isAuthError() || statusCode == 429) {
            return Severity.WARNING;
        } else if (statusCode >= 400) {
            return Severity.INFO;
        } else {
            return Severity.DEBUG;
        }
    }
    
    /**
     * Severity level enumeration
     */
    public enum Severity {
        DEBUG, INFO, WARNING, ERROR, CRITICAL
    }
    
    /**
     * Check if error should trigger alerts
     */
    public boolean shouldAlert() {
        return getSeverity().ordinal() >= Severity.WARNING.ordinal();
    }
    
    /**
     * Get recommended action based on error type
     */
    public String getRecommendedAction() {
        switch (getErrorCategory()) {
            case AUTHENTICATION:
                return "Check credentials and token validity. Re-authenticate if necessary.";
            case RATE_LIMIT:
                return "Reduce request frequency. Wait " + getRetryAfterSeconds() + " seconds before retrying.";
            case CLIENT_ERROR:
                return "Check request parameters and API documentation.";
            case SERVER_ERROR:
                return "Retry after " + getRetryAfterSeconds() + " seconds. Check Schwab API status.";
            case NETWORK_ERROR:
                return "Check network connectivity and DNS resolution.";
            default:
                return "Review error details and contact support if issue persists.";
        }
    }
    
    /**
     * Get error context for structured logging
     */
    public Map<String, Object> getErrorContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("statusCode", statusCode);
        context.put("errorCode", errorCode);
        context.put("category", getErrorCategory().name());
        context.put("severity", getSeverity().name());
        context.put("retryable", isRetryable());
        context.put("responseTime", responseTime);
        
        if (errorDetails.containsKey("description")) {
            context.put("description", errorDetails.get("description"));
        }
        
        return context;
    }
    
    /**
     * Common exception factory methods
     */
    public static SchwabApiException unauthorized(String message) {
        return new SchwabApiException(401, message, "UNAUTHORIZED", null, (Throwable) null);
    }
    
    public static SchwabApiException forbidden(String message) {
        return new SchwabApiException(403, message, "FORBIDDEN", null, (Throwable) null);
    }
    
    public static SchwabApiException notFound(String message) {
        return new SchwabApiException(404, message, "NOT_FOUND", null, (Throwable) null);
    }
    
    public static SchwabApiException rateLimited(String message, long retryAfterSeconds) {
        Map<String, Object> details = new HashMap<>();
        details.put("Retry-After", String.valueOf(retryAfterSeconds));
        return new SchwabApiException(429, message, "RATE_LIMITED", details, (Throwable) null);
    }
    
    public static SchwabApiException serverError(String message) {
        return new SchwabApiException(500, message, "SERVER_ERROR", null, (Throwable) null);
    }
    
    public static SchwabApiException timeout(String message) {
        return new SchwabApiException(408, message, "TIMEOUT", null, (Throwable) null);
    }
    
    public static SchwabApiException networkError(String message, Throwable cause) {
        return new SchwabApiException(0, message, "NETWORK_ERROR", null, cause);
    }
    
    public static SchwabApiException configurationError(String message) {
        return new SchwabApiException(0, message, "CONFIGURATION_ERROR", null, (Throwable) null);
    }
    
    public static SchwabApiException tokenError(String message) {
        return new SchwabApiException(401, message, "TOKEN_ERROR", null, (Throwable) null);
    }
    
    public static SchwabApiException validationError(String message) {
        return new SchwabApiException(400, message, "VALIDATION_ERROR", null, (Throwable) null);
    }
    
    /**
     * Builder pattern for creating exceptions with detailed context
     */
    public static class Builder {
        private int statusCode;
        private String message;
        private String errorCode;
        private Map<String, Object> details = new HashMap<>();
        private long responseTime;
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
        
        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details.putAll(details);
            return this;
        }
        
        public Builder responseTime(long responseTime) {
            this.responseTime = responseTime;
            return this;
        }
        
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }
        
        public SchwabApiException build() {
            if (cause != null) {
                return new SchwabApiException(statusCode, message, errorCode, details, cause);
            } else {
                return new SchwabApiException(statusCode, message, errorCode, details, responseTime);
            }
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Enhanced toString with structured information
     */
    @Override
    public String toString() {
        return String.format("SchwabApiException{statusCode=%d, errorCode='%s', category=%s, severity=%s, retryable=%s, message='%s', responseTime=%dms}", 
                statusCode, errorCode, getErrorCategory(), getSeverity(), isRetryable(), getMessage(), responseTime);
    }
    
    /**
     * Get a user-friendly error message
     */
    public String getUserFriendlyMessage() {
        switch (getErrorCategory()) {
            case AUTHENTICATION:
                return "Authentication failed. Please check your credentials and try again.";
            case RATE_LIMIT:
                return "Too many requests. Please wait a moment and try again.";
            case CLIENT_ERROR:
                return "Invalid request. Please check your input and try again.";
            case SERVER_ERROR:
                return "Service temporarily unavailable. Please try again later.";
            case NETWORK_ERROR:
                return "Network connection error. Please check your internet connection.";
            default:
                return "An unexpected error occurred. Please try again or contact support.";
        }
    }
    
    /**
     * Check if this exception indicates a temporary issue
     */
    public boolean isTemporary() {
        return isRetryable() || getErrorCategory() == ErrorCategory.NETWORK_ERROR;
    }
    
    /**
     * Get the appropriate HTTP status code for API responses
     */
    public int getHttpStatusCode() {
        return statusCode > 0 ? statusCode : 500; // Default to 500 for non-HTTP errors
    }
}
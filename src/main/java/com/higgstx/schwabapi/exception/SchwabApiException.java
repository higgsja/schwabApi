package com.higgstx.schwabapi.exception;

import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.util.HttpUtils;
import com.higgstx.schwabapi.util.JsonUtils;
import com.higgstx.schwabapi.util.UtilityClass;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception for Schwab API errors using @Getter
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
    
    // Constructor for simple cases
    public SchwabApiException(String message) {
        this(0, message, "UNKNOWN_ERROR", new HashMap<>(), null);
    }
    
    // Constructor with cause
    public SchwabApiException(String message, Throwable cause) {
        this(0, message, "UNKNOWN_ERROR", new HashMap<>(), cause);
    }
    
    // Factory method for API response errors
    public static SchwabApiException fromApiResponse(String operation, ApiResponse response) {
        Map<String, Object> details = JsonUtils.extractErrorDetails(response.getBody(), UtilityClass.getObjectMapper());
        String errorCode = details.containsKey("error") ? details.get("error").toString() : "HTTP_" + response.getStatusCode();
        String errorMessage = JsonUtils.extractErrorMessage(response.getBody(), UtilityClass.getObjectMapper());
        String message = UtilityClass.buildErrorMessage(operation, errorMessage);
        
        return new SchwabApiException(response.getStatusCode(), message, errorCode, details, null);
    }
    
    // Status check methods
    public boolean isRetryable() {
        return HttpUtils.isRetryableStatusCode(statusCode);
    }
    
    public boolean isAuthError() {
        return statusCode == 401 || statusCode == 403;
    }
    
    public boolean isRateLimited() {
        return statusCode == 429;
    }
    
    // Factory methods for common error types
    public static SchwabApiException notFound(String message) {
        return new SchwabApiException(404, message, "NOT_FOUND", Map.of(), null);
    }
    
    public static SchwabApiException serverError(String message) {
        return new SchwabApiException(500, message, "SERVER_ERROR", Map.of(), null);
    }
    
    public static SchwabApiException networkError(String operation, Throwable cause) {
        return new SchwabApiException(0, UtilityClass.buildErrorMessage(operation, cause.getMessage()), "NETWORK_ERROR", Map.of(), cause);
    }
    
    public static SchwabApiException configurationError(String message) {
        return new SchwabApiException(0, message, "CONFIGURATION_ERROR", Map.of(), null);
    }
    
    public static SchwabApiException tokenError(String message) {
        return new SchwabApiException(401, message, "TOKEN_ERROR", Map.of(), null);
    }
    
    public static SchwabApiException validationError(String message) {
        return new SchwabApiException(400, message, "VALIDATION_ERROR", Map.of(), null);
    }
}

package com.higgstx.schwabapi.model;

import lombok.Value;
import java.util.Map;

/**
 * Immutable API response wrapper using @Value
 */
@Value
public class ApiResponse {
    int statusCode;
    String body;
    Map<String, String> headers;
    long responseTimeMillis;
    
    /**
     * Check if the response indicates success
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    /**
     * Check if the response indicates a client error
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    /**
     * Check if the response indicates a server error
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }
}
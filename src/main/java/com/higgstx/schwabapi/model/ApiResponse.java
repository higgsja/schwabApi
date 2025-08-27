package com.higgstx.schwabapi.model;

import java.util.Map;

public class ApiResponse {
    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;
    private final long responseTimeMillis;

    public ApiResponse(int statusCode, String body, Map<String, String> headers, long responseTimeMillis) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
        this.responseTimeMillis = responseTimeMillis;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public long getResponseTimeMillis() {
        return responseTimeMillis;
    }
}
package com.higgstx.schwabapi.util;

import com.higgstx.schwabapi.interceptor.*;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP utility functions with consistent @Slf4j usage
 */
@Slf4j
public final class HttpUtils {

    private HttpUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Build a standard OkHttpClient with timeout and optional logging
     */
    public static OkHttpClient buildHttpClient(int timeoutMs, boolean enableLogging) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .addInterceptor(new SchwabApiInterceptor());

        if (enableLogging) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(
                    message -> log.debug(message));
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        return builder.build();
    }

    /**
     * Build an OkHttpClient with default 30-second timeout
     */
    public static OkHttpClient buildHttpClient(boolean enableLogging) {
        return buildHttpClient(30000, enableLogging);
    }

    /**
     * Convert OkHttp Headers to a single-value Map using only OkHttp methods
     */
    public static Map<String, String> headersToSingleValueMap(Headers headers) {
        Map<String, String> result = new HashMap<>();
        for (String name : headers.names()) {
            result.put(name, headers.get(name));
        }
        return result;
    }

    /**
     * Create a trust-all SSL context for development/testing
     * WARNING: This should not be used in production
     */
    public static SSLContext createTrustAllSSLContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllX509TrustManager()},
                    new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all SSL context", e);
        }
    }

    /**
     * Check if an HTTP status code indicates success (2xx)
     */
    public static boolean isSuccessCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Check if an HTTP status code indicates a client error (4xx)
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Check if an HTTP status code indicates a server error (5xx)
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * Check if an HTTP status code indicates a retryable condition
     */
    public static boolean isRetryableStatusCode(int statusCode) {
        return statusCode >= 500 || statusCode == 429 || statusCode == 408 || statusCode == 503;
    }

    /**
     * Check if an HTTP status code indicates an authentication/authorization error
     */
    public static boolean isAuthError(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }

    /**
     * Get a user-friendly description of an HTTP status code
     */
    public static String getStatusDescription(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 408 -> "Request Timeout";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "HTTP " + statusCode;
        };
    }

    /**
     * Extract retry delay from headers (Retry-After)
     */
    public static long getRetryAfterSeconds(Map<String, String> headers, long defaultSeconds) {
        String retryAfter = headers.get("Retry-After");
        if (retryAfter == null) {
            retryAfter = headers.get("retry-after");
        }
        return ConversionUtils.parseRetryAfterSeconds(retryAfter, defaultSeconds);
    }

    /**
     * Create authorization header value for basic auth
     */
    public static String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return java.util.Base64.getEncoder().encodeToString(
                credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Create authorization header value for bearer token
     */
    public static String createBearerAuthHeader(String token) {
        return token;
    }

  

    /**
     * Check if an exception indicates a retryable network condition
     */
    public static boolean isRetryableException(Exception exception) {
        if (exception == null) {
            return false;
        }

        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timeout")
                    || lowerMessage.contains("connection")
                    || lowerMessage.contains("network")
                    || lowerMessage.contains("socket")
                    || lowerMessage.contains("host");
        }

        return exception instanceof java.net.SocketTimeoutException
                || exception instanceof java.net.ConnectException
                || exception instanceof java.net.UnknownHostException
                || exception instanceof java.io.IOException;
    }

    /**
     * Trust-all X509TrustManager for development/testing
     * WARNING: Do not use in production
     */
    public static class TrustAllX509TrustManager implements X509TrustManager {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
            // Trust all
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
            // Trust all
        }
    }
}
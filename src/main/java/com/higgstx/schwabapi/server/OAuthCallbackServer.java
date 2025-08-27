package com.higgstx.schwabapi.server;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OkHttp-based OAuth callback server that supports both HTTP and HTTPS
 */
public class OAuthCallbackServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OAuthCallbackServer.class);
    private final CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
    private MockWebServer server;
    private boolean isStarted = false;

    /**
     * Default constructor - creates HTTP server on any available port
     */
    public OAuthCallbackServer() {
        this.server = new MockWebServer();
    }

    /**
     * Constructor with specific port and protocol
     */
    public OAuthCallbackServer(int port, boolean https) throws IOException {
        this.server = new MockWebServer();
        
        if (https) {
            try {
                // Create a trust-all SSL context
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new TrustAllX509TrustManager()}, null);
                server.useHttps(sslContext.getSocketFactory(), false);
            } catch (Exception e) {
                throw new IOException("Failed to configure HTTPS: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Constructor with specific port (HTTP)
     */
    public OAuthCallbackServer(int port) throws IOException {
        this.server = new MockWebServer();
    }

    public CompletableFuture<String> startAndWaitForCode(long timeout, TimeUnit unit) {
        try {
            // Set up response for any request
            server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody(getSuccessHtml()));

            server.start();
            isStarted = true;

            int port = server.getPort();
            String protocol = server.url("/").isHttps() ? "HTTPS" : "HTTP";
            logger.info("Started OAuth callback server on {} port {}. Awaiting callback.", protocol, port);

            // Start background thread to handle requests
            CompletableFuture.runAsync(() -> {
                try {
                    while (isStarted && !authCodeFuture.isDone()) {
                        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
                        if (request != null) {
                            handleRequest(request);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("Error processing callback request: {}", e.getMessage());
                    authCodeFuture.completeExceptionally(e);
                }
            });

            // Set up timeout handling
            return authCodeFuture
                .orTimeout(timeout, unit)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        if (throwable instanceof java.util.concurrent.TimeoutException) {
                            logger.warn("Authorization timed out after {} {}.", timeout, unit.toString().toLowerCase());
                        } else {
                            logger.error("Authorization failed: {}", throwable.getMessage());
                        }
                    } else if (result != null) {
                        logger.info("Authorization code received successfully");
                    }
                    stopServer();
                });

        } catch (IOException e) {
            logger.error("Failed to start OAuth callback server: {}", e.getMessage(), e);
            authCodeFuture.completeExceptionally(e);
            return authCodeFuture;
        }
    }

    private void handleRequest(RecordedRequest request) {
        String path = request.getPath();
        logger.debug("Received {} request to {}", request.getMethod(), path);

        try {
            if (path != null && path.contains("code=")) {
                String authCode = extractAuthCode(path);
                if (authCode != null && !authCode.isEmpty()) {
                    logger.info("Authorization code received: {}...", authCode.substring(0, Math.min(10, authCode.length())));
                    authCodeFuture.complete(authCode);
                } else {
                    logger.error("Failed to extract authorization code from path: {}", path);
                    authCodeFuture.completeExceptionally(new RuntimeException("Failed to extract authorization code"));
                }
            } else if (path != null && path.contains("error=")) {
                String error = extractErrorInfo(path);
                logger.error("OAuth error received: {}", error);
                authCodeFuture.completeExceptionally(new RuntimeException("OAuth error: " + error));
            } else {
                logger.error("No authorization code or error found in callback. Path: {}", path);
                authCodeFuture.completeExceptionally(new RuntimeException("No authorization code found in callback"));
            }
        } catch (Exception e) {
            logger.error("Error processing OAuth callback: {}", e.getMessage(), e);
            authCodeFuture.completeExceptionally(e);
        }
    }

    private String extractAuthCode(String path) {
        try {
            if (path == null || !path.contains("?")) {
                return null;
            }

            String query = path.substring(path.indexOf("?") + 1);
            String[] params = query.split("&");
            
            for (String param : params) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "code".equals(pair[0])) {
                    return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing authorization code from path '{}': {}", path, e.getMessage());
        }
        return null;
    }

    private String extractErrorInfo(String path) {
        try {
            if (path == null || !path.contains("?")) {
                return "Unknown error";
            }

            String query = path.substring(path.indexOf("?") + 1);
            String[] params = query.split("&");
            StringBuilder errorInfo = new StringBuilder();

            for (String param : params) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && (pair[0].equals("error") || pair[0].equals("error_description"))) {
                    if (errorInfo.length() > 0) {
                        errorInfo.append(", ");
                    }
                    errorInfo.append(pair[0]).append("=").append(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                }
            }

            return errorInfo.length() > 0 ? errorInfo.toString() : "Unknown error";

        } catch (Exception e) {
            logger.error("Error parsing error info from path '{}': {}", path, e.getMessage());
            return "Error parsing error information";
        }
    }

    private String getSuccessHtml() {
        return """
            <html>
            <head><title>Authorization Successful</title></head>
            <body>
                <h1>Authorization Successful!</h1>
                <p>
                    Your Schwab API authorization was completed successfully.
                    You can now close this window and return to the application.
                </p>
            </body>
            </html>
            """;
    }

    public int getPort() {
        if (server != null && isStarted) {
            return server.getPort();
        }
        return -1;
    }

    public boolean isRunning() {
        return isStarted && server != null;
    }

    public boolean isHttps() {
        return server != null && server.url("/").isHttps();
    }

    private void stopServer() {
        if (server != null && isStarted) {
            try {
                server.shutdown();
                isStarted = false;
                logger.debug("OAuth callback server stopped");
            } catch (Exception e) {
                logger.warn("Warning during server shutdown: {}", e.getMessage());
            }
        }
    }

    private static class TrustAllX509TrustManager implements X509TrustManager {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    }

    @Override
    public void close() {
        stopServer();
    }
}
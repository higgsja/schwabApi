//v8
package com.higgstx.schwabapi.server;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OAuth callback server using HTTPS with self-signed certificate for Schwab API
 */
public class OAuthCallbackServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OAuthCallbackServer.class);
    private final CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
    private MockWebServer server;
    private boolean isStarted = false;

    /**
     * Default constructor - creates HTTPS server on port 8182
     */
    public OAuthCallbackServer() throws IOException {
        this.server = new MockWebServer();
        try {
            // Set up self-signed SSL certificate for localhost
            setupSSL();
        } catch (Exception e) {
            throw new IOException("Failed to setup SSL for HTTPS server: " + e.getMessage(), e);
        }
    }

    /**
     * Constructor with specific port (HTTPS only) - deprecated, use default constructor for port 8182
     */
    @Deprecated
    public OAuthCallbackServer(int port) throws IOException {
        this(); // Delegate to default constructor
    }

    private void setupSSL() throws Exception {
        // Create a trust-all SSL context for self-signed certificates
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new TrustAllX509TrustManager()}, new java.security.SecureRandom());
        
        // Configure MockWebServer to use HTTPS
        server.useHttps(sslContext.getSocketFactory(), false);
        
        logger.debug("SSL context configured for MockWebServer");
    }

    public CompletableFuture<String> startAndWaitForCode(long timeout, TimeUnit unit) {
        try {
            // Set up multiple responses to handle various requests (favicon, multiple callbacks, etc.)
            for (int i = 0; i < 10; i++) {
                server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setHeader("Access-Control-Allow-Origin", "*")
                    .setBody(getSuccessHtml()));
            }

            // Start server on port 8182
            server.start(8182);
            isStarted = true;

            int port = server.getPort();
            if (port != 8182) {
                server.shutdown();
                throw new IOException("Could not start server on required port 8182, got port " + port);
            }
            
            logger.info("Started OAuth callback server on HTTPS port {}. Awaiting callback.", port);

            // Start background thread to handle requests
            CompletableFuture.runAsync(() -> {
                try {
                    logger.info("Request handler thread started, waiting for OAuth callback...");
                    while (isStarted && !authCodeFuture.isDone()) {
                        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
                        if (request != null) {
                            logger.info("Received request: {} {}", request.getMethod(), request.getPath());
                            handleRequest(request);
                        } else {
                            logger.debug("No request received, continuing to wait...");
                        }
                    }
                    logger.info("Request handler thread exiting");
                } catch (InterruptedException e) {
                    logger.warn("Request handler thread interrupted");
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("Error processing callback request: {}", e.getMessage(), e);
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
        String method = request.getMethod();
        logger.info("Processing {} request to path: '{}'", method, path);

        try {
            // Handle any request to root or with parameters as potential OAuth callback
            if (path != null) {
                logger.debug("Full request details - Path: '{}', Query: '{}'", path, 
                    path.contains("?") ? path.substring(path.indexOf("?") + 1) : "none");
                
                if (path.contains("code=")) {
                    logger.info("Found authorization code parameter in request");
                    String authCode = extractAuthCode(path);
                    if (authCode != null && !authCode.isEmpty()) {
                        logger.info("Successfully extracted authorization code: {}...", 
                            authCode.substring(0, Math.min(10, authCode.length())));
                        authCodeFuture.complete(authCode);
                        return;
                    } else {
                        logger.error("Authorization code parameter found but extraction failed from path: {}", path);
                    }
                } else if (path.contains("error=")) {
                    logger.warn("OAuth error found in callback");
                    String error = extractErrorInfo(path);
                    logger.error("OAuth error received: {}", error);
                    authCodeFuture.completeExceptionally(new RuntimeException("OAuth error: " + error));
                    return;
                } else {
                    logger.warn("Request received but no 'code' or 'error' parameter found. Path: '{}'", path);
                    // Don't fail immediately - this might be a preflight request or favicon request
                    // Just log it and continue waiting
                }
            } else {
                logger.warn("Received request with null path");
            }
        } catch (Exception e) {
            logger.error("Exception while processing OAuth callback: {}", e.getMessage(), e);
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
            <!DOCTYPE html>
            <html>
            <head>
                <title>Authorization Successful</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background: #f5f5f5; }
                    .success { color: #28a745; }
                    .container { max-width: 500px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .warning { color: #fd7e14; font-size: 14px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1 class="success">✓ Authorization Successful!</h1>
                    <p>
                        Your Schwab API authorization was completed successfully.
                        You can now close this window and return to the application.
                    </p>
                    <div class="warning">
                        <strong>Note:</strong> Your browser may have shown a security warning due to the self-signed certificate.
                        This is normal for local OAuth callbacks.
                    </div>
                    <script>
                        // Auto-close after 5 seconds
                        setTimeout(function() {
                            window.close();
                        }, 5000);
                    </script>
                </div>
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
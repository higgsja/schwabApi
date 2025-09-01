package com.higgstx.schwabapi.server;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTPS OAuth callback server using OkHttp's built-in TLS support
 */
public class OkHttpSSLServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OkHttpSSLServer.class);
    private final CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
    private MockWebServer server;
    private boolean isStarted = false;

    public CompletableFuture<String> startAndWaitForCode(long timeout, TimeUnit unit) throws IOException {
        try {
            server = new MockWebServer();
            
            // Generate self-signed certificate using OkHttp's utilities
            logger.info("Generating self-signed certificate for localhost...");
            HeldCertificate localhostCertificate = new HeldCertificate.Builder()
                .addSubjectAlternativeName("127.0.0.1")
                .addSubjectAlternativeName("localhost")
                .validityInterval(System.currentTimeMillis(), System.currentTimeMillis() + Duration.ofDays(365).toMillis())
                .build();

            HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(localhostCertificate)
                .build();

            // Configure MockWebServer with SSL
            server.useHttps(serverCertificates.sslSocketFactory(), false);
            logger.info("SSL configured successfully");
            
            // Queue responses for multiple requests
            for (int i = 0; i < 20; i++) {
                server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setHeader("Access-Control-Allow-Origin", "*")
                    .setBody(getSuccessHtml()));
            }

            // Start server on port 8182
            server.start(8182);
            isStarted = true;
            
            logger.info("HTTPS server started successfully on https://127.0.0.1:8182");
            logger.info("Server URL: {}", server.url("/"));
            
            // Start request handling
            CompletableFuture.runAsync(() -> {
                logger.info("Request handler started");
                try {
                    while (isStarted && !authCodeFuture.isDone()) {
                        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
                        if (request != null) {
                            handleRequest(request);
                        }
                    }
                    logger.info("Request handler finished");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("Request handler interrupted");
                } catch (Exception e) {
                    logger.error("Request handler error", e);
                    if (!authCodeFuture.isDone()) {
                        authCodeFuture.completeExceptionally(e);
                    }
                }
            });

            return authCodeFuture
                .orTimeout(timeout, unit)
                .whenComplete((result, throwable) -> {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        logger.warn("Authorization timed out after {} {}", timeout, unit);
                    } else if (throwable != null) {
                        logger.error("Authorization failed", throwable);
                    } else if (result != null) {
                        logger.info("Authorization completed successfully");
                    }
                    stopServer();
                });
            
        } catch (Exception e) {
            logger.error("Failed to start HTTPS server", e);
            throw new IOException("Server startup failed: " + e.getMessage(), e);
        }
    }

    private void handleRequest(RecordedRequest request) {
        String method = request.getMethod();
        String path = request.getPath();
        
        logger.info("Received {} request to: {}", method, path);
        
        try {
            if (path != null && path.contains("code=")) {
                String authCode = extractAuthCode(path);
                if (authCode != null && !authCode.isEmpty()) {
                    logger.info("Authorization code extracted: {}...", 
                        authCode.substring(0, Math.min(10, authCode.length())));
                    authCodeFuture.complete(authCode);
                } else {
                    logger.error("Failed to extract authorization code from: {}", path);
                }
            } else if (path != null && path.contains("error=")) {
                String error = extractErrorInfo(path);
                logger.error("OAuth error received: {}", error);
                authCodeFuture.completeExceptionally(new RuntimeException("OAuth error: " + error));
            } else {
                logger.debug("Non-OAuth request received: {} (this is normal for favicon, etc.)", path);
            }
        } catch (Exception e) {
            logger.error("Error processing request", e);
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
            logger.error("Error extracting auth code from: {}", path, e);
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
            logger.error("Error extracting error info from: {}", path, e);
            return "Error parsing error information";
        }
    }

    private String getSuccessHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>OAuth Authorization Successful</title>
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif;
                        text-align: center; 
                        padding: 50px; 
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        margin: 0;
                    }
                    .container { 
                        max-width: 500px; 
                        margin: 0 auto; 
                        background: rgba(255,255,255,0.1); 
                        padding: 40px; 
                        border-radius: 12px; 
                        backdrop-filter: blur(10px);
                        box-shadow: 0 8px 32px rgba(0,0,0,0.2);
                    }
                    .success { color: #4CAF50; font-size: 3em; margin: 0; }
                    .title { margin: 20px 0; font-size: 1.5em; }
                    .message { margin: 20px 0; opacity: 0.9; line-height: 1.5; }
                    .note { font-size: 0.9em; opacity: 0.7; margin-top: 30px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="success">✓</div>
                    <h1 class="title">Authorization Successful!</h1>
                    <p class="message">
                        Your Schwab API authorization has been completed successfully.<br>
                        You can now close this window and return to the application.
                    </p>
                    <div class="note">
                        This window will close automatically in 5 seconds.
                    </div>
                    <script>
                        setTimeout(() => {
                            try {
                                window.close();
                            } catch (e) {
                                document.body.innerHTML = '<div style="padding:50px">You can safely close this window now.</div>';
                            }
                        }, 5000);
                    </script>
                </div>
            </body>
            </html>
            """;
    }

    public boolean isRunning() {
        return isStarted && server != null;
    }

    private void stopServer() {
        if (server != null && isStarted) {
            try {
                server.shutdown();
                isStarted = false;
                logger.info("HTTPS server stopped");
            } catch (Exception e) {
                logger.warn("Error stopping server", e);
            }
        }
    }

    @Override
    public void close() {
        stopServer();
    }
}
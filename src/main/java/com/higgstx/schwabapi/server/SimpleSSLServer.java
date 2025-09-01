package com.higgstx.schwabapi.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Minimal HTTPS server for OAuth callbacks
 */
public class SimpleSSLServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SimpleSSLServer.class);
    private final CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
    private HttpsServer server;
    private boolean isStarted = false;

    public CompletableFuture<String> startAndWaitForCode(long timeout, TimeUnit unit) throws IOException {
        try {
            logger.info("Creating minimal HTTPS server on port 8182...");
            
            // Create HTTPS server
            server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 8182), 0);
            logger.info("Server created successfully");
            
            // Create minimal SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new AcceptAllTrustManager()}, null);
            
            // Configure HTTPS
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            logger.info("SSL context configured");
            
            // Create handler
            server.createContext("/", new CallbackHandler());
            server.setExecutor(null);
            
            // Start server
            server.start();
            isStarted = true;
            
            logger.info("HTTPS server started on https://127.0.0.1:8182");
            
            return authCodeFuture
                .orTimeout(timeout, unit)
                .whenComplete((result, throwable) -> {
                    stopServer();
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        logger.warn("Authorization timed out");
                    } else if (throwable != null) {
                        logger.error("Authorization failed: {}", throwable.getMessage());
                    }
                });
                
        } catch (Exception e) {
            logger.error("Failed to start HTTPS server", e);
            throw new IOException("Server start failed: " + e.getMessage(), e);
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().toString();
            logger.info("Request: {} {}", exchange.getRequestMethod(), path);
            
            // Always send success response
            String html = """
                <!DOCTYPE html><html><head><title>OAuth Success</title></head>
                <body style='text-align:center;padding:50px;font-family:Arial'>
                <h2 style='color:green'>✓ Authorization Successful!</h2>
                <p>You can close this window and return to the application.</p>
                <script>setTimeout(()=>window.close(),3000);</script>
                </body></html>
                """;
            
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length());
            exchange.getResponseBody().write(html.getBytes());
            exchange.getResponseBody().close();
            
            // Process OAuth callback
            if (path.contains("code=")) {
                String code = extractCode(path);
                if (code != null) {
                    logger.info("Got auth code: {}...", code.substring(0, Math.min(8, code.length())));
                    authCodeFuture.complete(code);
                }
            } else if (path.contains("error=")) {
                logger.error("OAuth error in path: {}", path);
                authCodeFuture.completeExceptionally(new RuntimeException("OAuth error"));
            }
        }
    }
    
    private String extractCode(String path) {
        try {
            int start = path.indexOf("code=") + 5;
            int end = path.indexOf("&", start);
            String code = (end > 0) ? path.substring(start, end) : path.substring(start);
            return URLDecoder.decode(code, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to extract code", e);
            return null;
        }
    }

    private void stopServer() {
        if (server != null && isStarted) {
            server.stop(0);
            isStarted = false;
            logger.info("Server stopped");
        }
    }

    public boolean isRunning() {
        return isStarted;
    }

    private static class AcceptAllTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }

    @Override
    public void close() {
        stopServer();
    }
}
package com.higgstx.schwab.client;

import com.higgstx.schwab.config.SchwabConfig;
import com.higgstx.schwab.model.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;

/**
 * A manual OAuth client for the Schwab API. This is used as a fallback if the
 * local server cannot be started.
 */
public class ManualOAuthClient {

    private static final Logger logger = LoggerFactory.getLogger(ManualOAuthClient.class);

    private final SchwabOAuthClient schwabOAuthClient;

    public ManualOAuthClient() {
        this.schwabOAuthClient = new SchwabOAuthClient();
    }

    /**
     * Initiates the manual OAuth flow.
     * @return A {@link TokenResponse} with the new tokens.
     */
    public TokenResponse startManualAuthorization() throws Exception {
        System.out.println("❌ Local server is not available or failed to start. Falling back to manual authorization.");

        // Get the authorization URL from the SchwabOAuthClient
        String authUrl = schwabOAuthClient.getAuthorizationUrl();

        System.out.println("\nFollow these steps to authorize your application:");
        System.out.println("1. Copy this URL and paste it into your web browser:");
        System.out.println(authUrl);
        System.out.println("\n2. Sign in to your Schwab account and approve the application.");
        System.out.println("\n3. You will be redirected to a blank page with a URL that starts with '" + SchwabConfig.REDIRECT_URI + "'.");
        System.out.println("\n4. Copy the entire URL from the address bar and paste it here:");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String redirectUriResponse = reader.readLine();

        // Extract the authorization code from the URL
        String authCode = extractAuthCode(redirectUriResponse);

        if (authCode != null && !authCode.isEmpty()) {
            System.out.println("✅ Authorization code received. Exchanging for tokens...");
            return schwabOAuthClient.getTokens(authCode);
        } else {
            System.out.println("❌ Failed to get authorization code from the URL. Please try again.");
            return null;
        }
    }

    /**
     * Extracts the authorization code from the redirect URL.
     * @param url The full redirect URL.
     * @return The authorization code, or null if not found.
     */
    private String extractAuthCode(String url) {
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if ("code".equals(pair[0])) {
                        return pair[1];
                    }
                }
            }
        } catch (URISyntaxException e) {
            logger.error("Error parsing URL: {}", e.getMessage());
        }
        return null;
    }
}
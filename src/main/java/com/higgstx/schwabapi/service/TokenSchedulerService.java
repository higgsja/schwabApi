package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.exception.SchwabApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TokenSchedulerService {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenSchedulerService.class);
    
    private final TokenManager tokenManager;
    
    public TokenSchedulerService(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }
    
    @Scheduled(fixedRate = 240000) // Every 4 minutes
    public void proactiveTokenRefresh() {
        try {
            if (tokenManager.needsRefresh()) {
                logger.info("Proactive token refresh triggered");
                tokenManager.forceTokenRefresh();
                logger.info("Proactive token refresh completed successfully");
            }
        } catch (SchwabApiException e) {
            logger.error("Proactive token refresh failed: {}", e.getMessage());
        }
    }
}
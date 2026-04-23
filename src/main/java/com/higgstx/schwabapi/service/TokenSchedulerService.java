package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.exception.SchwabApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenSchedulerService {
    
    private final TokenManager tokenManager;
    
    @Scheduled(fixedRate = 240000) // Every 4 minutes
    public void proactiveTokenRefresh() {
        try {
            if (tokenManager.needsRefresh()) {
                log.info("Proactive token refresh triggered");
                tokenManager.forceTokenRefresh();
                log.info("Proactive token refresh completed successfully");
            }
        } catch (SchwabApiException e) {
            log.error("Proactive token refresh failed: {}", e.getMessage());
        }
    }
}
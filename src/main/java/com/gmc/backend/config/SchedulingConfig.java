package com.gmc.backend.config;

import com.gmc.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig {

    private final OrderService orderService;

    // Every 5 minutes: cancel card orders left unpaid too long so their stock is released
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void expireUnpaidCardOrders() {
        try {
            int expired = orderService.expireUnpaidCardOrders();
            if (expired > 0) {
                log.info("Cancelled {} unpaid card order(s)", expired);
            }
        } catch (RuntimeException e) {
            log.error("Unpaid order expiry failed: {}", e.getMessage());
        }
    }
}

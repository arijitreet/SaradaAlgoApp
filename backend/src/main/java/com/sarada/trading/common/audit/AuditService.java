package com.sarada.trading.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Append-only audit trail. Async so the trading hot path never blocks on it;
 * failures are logged, never propagated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Async("appExecutor")
    public void log(String category, String action, String detail) {
        log(category, action, detail, "SYSTEM");
    }

    @Async("appExecutor")
    public void log(String category, String action, String detail, String actor) {
        try {
            repository.save(new AuditLog(category, action, detail, actor));
        } catch (Exception e) {
            log.error("Audit write failed: {} {} — {}", category, action, e.getMessage());
        }
    }
}

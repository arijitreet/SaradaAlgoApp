package com.sarada.trading.risk.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.events.DomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Flattens everything at the 15:05 IST cutoff — no overnight option risk, ever. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForceExitScheduler {

    private final ApplicationEventPublisher events;
    private final AuditService audit;

    @Scheduled(cron = "0 5 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void forceExitAll() {
        log.info("15:05 IST — force exit window reached");
        audit.log("RISK", "FORCE_EXIT", "Session cutoff reached; flattening open positions");
        events.publishEvent(new DomainEvents.ForceExitRequested("Session force-exit at 15:05 IST"));
    }
}

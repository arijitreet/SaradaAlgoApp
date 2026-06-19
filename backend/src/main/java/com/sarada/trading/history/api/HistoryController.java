package com.sarada.trading.history.api;

import com.sarada.trading.common.audit.AuditLog;
import com.sarada.trading.common.audit.AuditLogRepository;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.infra.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trade history (closed positions) and the audit trail. */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final PositionRepository positions;
    private final AuditLogRepository auditLogs;

    @GetMapping("/trades")
    public Page<PositionEntity> trades(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return positions.findByStatusOrderByClosedAtDesc(
                PositionEntity.Status.CLOSED, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/audit")
    public Page<AuditLog> audit(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size,
                                @RequestParam(required = false) String category) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200));
        return category == null || category.isBlank()
                ? auditLogs.findAllByOrderByCreatedAtDesc(pageable)
                : auditLogs.findByCategoryOrderByCreatedAtDesc(category, pageable);
    }
}

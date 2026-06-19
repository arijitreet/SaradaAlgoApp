package com.sarada.trading.common.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String action;

    private String detail;

    @Column(nullable = false)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuditLog(String category, String action, String detail, String actor) {
        this.category = category;
        this.action = action;
        this.detail = detail;
        this.actor = actor;
        this.createdAt = Instant.now();
    }
}

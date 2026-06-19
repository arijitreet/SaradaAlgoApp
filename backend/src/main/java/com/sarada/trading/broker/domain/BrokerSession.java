package com.sarada.trading.broker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Persisted Kite OAuth session. Tokens are AES-GCM encrypted before storage. */
@Entity
@Table(name = "broker_sessions")
@Getter
@Setter
@NoArgsConstructor
public class BrokerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String broker = "ZERODHA";

    @Column(name = "kite_user_id")
    private String kiteUserId;

    @Column(name = "access_token_encrypted")
    private String accessTokenEncrypted;

    @Column(name = "public_token_encrypted")
    private String publicTokenEncrypted;

    @Column(name = "login_time")
    private Instant loginTime;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isExpired() {
        return expiresAt == null || Instant.now().isAfter(expiresAt);
    }
}

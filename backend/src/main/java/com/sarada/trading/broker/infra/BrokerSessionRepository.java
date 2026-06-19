package com.sarada.trading.broker.infra;

import com.sarada.trading.broker.domain.BrokerSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrokerSessionRepository extends JpaRepository<BrokerSession, Long> {

    Optional<BrokerSession> findFirstByActiveTrueOrderByLoginTimeDesc();
}

package com.sarada.trading.broker.infra.kite;

import com.sarada.trading.broker.domain.BrokerSession;
import com.sarada.trading.broker.infra.BrokerSessionRepository;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.crypto.TokenEncryptor;
import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Builds authenticated KiteConnect clients from the persisted (encrypted) session. */
@Component
@RequiredArgsConstructor
public class KiteClientFactory {

    private final AppProperties props;
    private final BrokerSessionRepository sessions;
    private final TokenEncryptor encryptor;

    public KiteConnect unauthenticated() {
        return new KiteConnect(props.kite().apiKey());
    }

    public Optional<String> activeAccessToken() {
        return sessions.findFirstByActiveTrueOrderByLoginTimeDesc()
                .filter(session -> !session.isExpired())
                .map(BrokerSession::getAccessTokenEncrypted)
                .map(encryptor::decrypt);
    }

    public Optional<KiteConnect> authenticated() {
        return sessions.findFirstByActiveTrueOrderByLoginTimeDesc()
                .filter(session -> !session.isExpired())
                .map(session -> {
                    KiteConnect kite = new KiteConnect(props.kite().apiKey());
                    kite.setUserId(session.getKiteUserId());
                    kite.setAccessToken(encryptor.decrypt(session.getAccessTokenEncrypted()));
                    return kite;
                });
    }
}

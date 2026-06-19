package com.sarada.trading.broker.api;

import com.sarada.trading.broker.application.BrokerAuthService;
import com.sarada.trading.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/broker")
@RequiredArgsConstructor
public class BrokerController {

    private final BrokerAuthService brokerAuthService;
    private final AppProperties props;

    @GetMapping("/login-url")
    public Map<String, String> loginUrl() {
        return Map.of("url", brokerAuthService.loginUrl());
    }

    /** Kite redirects here after the user approves access on kite.trade. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("request_token") String requestToken) {
        brokerAuthService.completeLogin(requestToken);
        String frontend = props.security().corsOrigins().split(",")[0];
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontend + "/settings?broker=connected"))
                .build();
    }

    @GetMapping("/status")
    public BrokerAuthService.BrokerStatus status() {
        return brokerAuthService.status();
    }
}

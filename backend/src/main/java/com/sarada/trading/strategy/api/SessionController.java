package com.sarada.trading.strategy.api;

import com.sarada.trading.strategy.application.TradingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final TradingSessionService sessionService;

    @PostMapping("/start")
    public TradingSessionService.SessionStatus start(Principal principal) {
        sessionService.start(principal.getName());
        return sessionService.status();
    }

    @PostMapping("/stop")
    public TradingSessionService.SessionStatus stop(Principal principal) {
        sessionService.stop(principal.getName(), "Manual stop");
        return sessionService.status();
    }

    @GetMapping("/status")
    public TradingSessionService.SessionStatus status() {
        return sessionService.status();
    }
}

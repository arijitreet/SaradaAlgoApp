package com.sarada.trading.positions.api;

import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.positions.application.PositionService;
import com.sarada.trading.positions.domain.PositionEntity;
import com.sarada.trading.positions.infra.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionRepository positions;
    private final PositionService positionService;
    private final LivePriceCache priceCache;
    private final TradingClock clock;

    @GetMapping("/active")
    public List<PositionService.PositionView> active() {
        return positions.findByStatus(PositionEntity.Status.OPEN).stream()
                .map(p -> positionService.toView(p,
                        priceCache.ltp(p.getInstrumentToken()).orElse(null)))
                .toList();
    }

    @GetMapping("/today")
    public List<PositionService.PositionView> today() {
        return positions.findByTradingDayOrderByOpenedAtDesc(clock.tradingDay()).stream()
                .map(p -> positionService.toView(p,
                        priceCache.ltp(p.getInstrumentToken()).orElse(null)))
                .toList();
    }

    /** Manual exit from the dashboard's active trade panel. */
    @PostMapping("/{id}/exit")
    public PositionService.PositionView exit(@PathVariable long id,
                                             java.security.Principal principal) {
        PositionEntity closed = positionService.exit(id, "MANUAL", principal.getName());
        return positionService.toView(closed, closed.getExitPrice());
    }
}

package com.sarada.trading.analytics.api;

import com.sarada.trading.analytics.application.AnalyticsService;
import com.sarada.trading.analytics.domain.DailyStats;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analytics;

    /** period: daily | weekly | monthly */
    @GetMapping("/summary")
    public AnalyticsService.Summary summary(@RequestParam(defaultValue = "daily") String period) {
        return analytics.summary(period);
    }

    @GetMapping("/equity-curve")
    public List<AnalyticsService.EquityPoint> equityCurve(@RequestParam(defaultValue = "30") int days) {
        return analytics.equityCurve(Math.min(days, 365));
    }

    @GetMapping("/daily")
    public List<DailyStats> daily(@RequestParam(defaultValue = "30") int days) {
        return analytics.dailyBreakdown(Math.min(days, 365));
    }
}

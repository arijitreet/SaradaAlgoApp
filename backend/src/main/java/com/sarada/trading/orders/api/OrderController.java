package com.sarada.trading.orders.api;

import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.orders.domain.OrderEntity;
import com.sarada.trading.orders.infra.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orders;
    private final TradingClock clock;

    @GetMapping
    public List<OrderEntity> byDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return orders.findByTradingDayOrderByPlacedAtDesc(day != null ? day : clock.tradingDay());
    }
}

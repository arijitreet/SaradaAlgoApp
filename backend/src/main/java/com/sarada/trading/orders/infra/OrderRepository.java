package com.sarada.trading.orders.infra;

import com.sarada.trading.orders.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByTradingDayOrderByPlacedAtDesc(LocalDate tradingDay);
}

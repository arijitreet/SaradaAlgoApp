package com.sarada.trading;

import com.sarada.trading.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
@EnableAsync(proxyTargetClass = true)
@EnableRetry(proxyTargetClass = true)
public class TradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }
}

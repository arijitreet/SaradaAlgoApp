package com.sarada.trading.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AsyncConfig {

    /** Pool used by @Async listeners (audit, analytics, WS fan-out). */
    @Bean(name = "appExecutor")
    public ThreadPoolTaskExecutor appExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("app-async-");
        executor.initialize();
        return executor;
    }

    /** Scheduler for session-window jobs (force exit, reconnect probes). */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("app-sched-");
        scheduler.initialize();
        return scheduler;
    }
}

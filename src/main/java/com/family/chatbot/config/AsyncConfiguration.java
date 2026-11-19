package com.family.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Microservice Bulkhead & Asynchronous Fault Isolation Configuration.
 * Allocates dedicated, isolated thread pools for Google Drive sync and Audit events.
 * If external Google Drive sync is slow or fails, the core WhatsApp webhook and file storage
 * continue running with zero impact.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean(name = "driveSyncExecutor")
    public Executor driveSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("DriveSyncWorker-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("AuditLogWorker-");
        executor.initialize();
        return executor;
    }
}

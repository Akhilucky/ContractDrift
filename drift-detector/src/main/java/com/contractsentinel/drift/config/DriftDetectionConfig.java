package com.contractsentinel.drift.config;

import com.contractsentinel.drift.diff.SemanticDiffEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class DriftDetectionConfig {

    @Value("${drift.detection.rate-limit:10}")
    private int rateLimit;

    @Value("${drift.detection.thread-pool-size:4}")
    private int threadPoolSize;

    @Bean
    public SemanticDiffEngine semanticDiffEngine() {
        return new SemanticDiffEngine();
    }

    @Bean("driftTaskExecutor")
    public Executor driftTaskExecutor() {
        return Executors.newFixedThreadPool(threadPoolSize);
    }

    public int getRateLimit() {
        return rateLimit;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }
}

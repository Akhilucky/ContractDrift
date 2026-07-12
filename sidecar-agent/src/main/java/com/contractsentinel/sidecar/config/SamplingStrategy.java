package com.contractsentinel.sidecar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SamplingStrategy {

    private static final Logger log = LoggerFactory.getLogger(SamplingStrategy.class);

    private final int maxSamplesPerMinute;
    private final double errorPriorityBoost;
    private final AtomicInteger sampleCount;
    private final AtomicReference<Instant> windowStart;

    public SamplingStrategy(
            @Value("${sampling.max-samples-per-minute:1000}") int maxSamplesPerMinute,
            @Value("${sampling.error-priority-boost:2.0}") double errorPriorityBoost) {
        this.maxSamplesPerMinute = maxSamplesPerMinute;
        this.errorPriorityBoost = errorPriorityBoost;
        this.sampleCount = new AtomicInteger(0);
        this.windowStart = new AtomicReference<>(Instant.now());
    }

    public boolean shouldSample(int statusCode) {
        resetIfWindowExpired();

        int count = sampleCount.get();
        if (count >= maxSamplesPerMinute) {
            return false;
        }

        double adjustedCapacity = maxSamplesPerMinute;
        if (isErrorStatus(statusCode)) {
            adjustedCapacity *= errorPriorityBoost;
        }

        double probability = Math.min(1.0, adjustedCapacity / maxSamplesPerMinute);

        if (ThreadLocalRandom.current().nextDouble() < probability) {
            return sampleCount.incrementAndGet() <= maxSamplesPerMinute;
        }

        return false;
    }

    private boolean isErrorStatus(int statusCode) {
        return statusCode >= 400;
    }

    private void resetIfWindowExpired() {
        Instant now = Instant.now();
        Instant start = windowStart.get();
        if (now.getEpochSecond() - start.getEpochSecond() >= 60) {
            if (windowStart.compareAndSet(start, now)) {
                int previous = sampleCount.getAndSet(0);
                log.debug("Sampling window reset. Previous count: {}", previous);
            }
        }
    }

    public int getCurrentSampleCount() {
        return sampleCount.get();
    }

    public int getMaxSamplesPerMinute() {
        return maxSamplesPerMinute;
    }
}

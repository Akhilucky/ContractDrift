package com.contractsentinel.ingestion.consumer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

@Service
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);

    private final Cache<String, Boolean> dedupCache;

    public DeduplicationService(@Value("${ingestion.dedup.window-duration:5m}") Duration windowDuration) {
        this.dedupCache = CacheBuilder.newBuilder()
                .expireAfterWrite(windowDuration)
                .maximumSize(1_000_000)
                .recordStats()
                .build();
    }

    public boolean isDuplicate(String contentHash) {
        try {
            return dedupCache.get(contentHash, () -> Boolean.FALSE);
        } catch (ExecutionException e) {
            log.warn("Dedup cache lookup failed for hash: {}", contentHash, e);
            return false;
        }
    }

    public void markProcessed(String contentHash) {
        dedupCache.put(contentHash, Boolean.TRUE);
    }
}

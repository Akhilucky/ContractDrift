package com.contractsentinel.registry.redis;

import com.contractsentinel.registry.model.Contract;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ContractCache {

    private static final Logger log = LoggerFactory.getLogger(ContractCache.class);
    private static final String KEY_PREFIX = "contract:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ContractCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void cacheContract(Contract contract) {
        String key = buildKey(contract.getProviderId(), contract.getConsumerId(), contract.getEndpoint());
        try {
            String json = objectMapper.writeValueAsString(contract);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize contract for cache: {}", e.getMessage());
        }
    }

    public Optional<Contract> getContract(String providerId, String consumerId, String endpoint) {
        String key = buildKey(providerId, consumerId, endpoint);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, Contract.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize contract from cache: {}", e.getMessage());
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void evict(String providerId, String consumerId, String endpoint) {
        String key = buildKey(providerId, consumerId, endpoint);
        redisTemplate.delete(key);
    }

    private String buildKey(String providerId, String consumerId, String endpoint) {
        return KEY_PREFIX + providerId + ":" + consumerId + ":" + endpoint;
    }
}

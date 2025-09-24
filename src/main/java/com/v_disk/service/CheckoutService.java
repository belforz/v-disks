package com.v_disk.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;


// no longer utilized

@Service
public class CheckoutService {

    private final StringRedisTemplate redis;
    private final Duration checkoutTtl;

    public CheckoutService(StringRedisTemplate redis, @Value("${app.checkout.ttl.seconds:1800}") long ttlSeconds) {
        this.redis = redis;
        this.checkoutTtl = Duration.ofSeconds(ttlSeconds);
    }

    private String keyFor(String paymentId) {
        return "checkout:" + paymentId;
    }

    
    public void save(String paymentId, Map<String, String> payload) {
        String key = keyFor(paymentId);
        redis.opsForHash().putAll(key, payload);
        redis.expire(key, checkoutTtl);
    }

    
    public boolean tryCreateMarker(String paymentId) {
        String key = keyFor(paymentId);
        Boolean created = redis.opsForValue().setIfAbsent(key + ":marker", "1", checkoutTtl);
        return Boolean.TRUE.equals(created);
    }

    public Map<String, String> get(String paymentId) {
        String key = keyFor(paymentId);
        Map<Object, Object> raw = redis.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> out = new HashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        return out;
    }

    public void clear(String paymentId) {
        redis.delete(keyFor(paymentId));
        redis.delete(keyFor(paymentId) + ":marker");
    }
}

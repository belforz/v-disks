package com.v_disk.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    // Redis for bag purposes

    private final StringRedisTemplate redis;

    private final Duration cartTtl;

    public CartService(StringRedisTemplate redis, @Value("${app.cart.ttl.seconds:86400}") long ttlSeconds) {
        this.redis = redis;
        this.cartTtl = Duration.ofSeconds(ttlSeconds);
    }

    private String keyFor(String userId) {
        return "cart:" + userId;
    }

    public void putItem(String userId, String vinylId, int quantity) {
        String key = keyFor(userId);
        redis.opsForHash().put(key, vinylId, String.valueOf(quantity));
        redis.expire(key, cartTtl);
    }

    public void removeItem(String userId, String vinylId) {
        String key = keyFor(userId);
        redis.opsForHash().delete(key, vinylId);
    }

    public Map<String, Integer> listItems(String userId) {
        String key = keyFor(userId);
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        if (entries == null)
            return Map.of();
        return entries.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> Integer.parseInt(String.valueOf(e.getValue()))));
    }

    public void clearCart(String userId) {
        redis.delete(keyFor(userId));
    }

    public void createCart(String userId, Map<String, Integer> items) {
        String key = keyFor(userId);
        Map<String, String> toStore = new HashMap<>();
        items.forEach((k, v) -> toStore.put(k, String.valueOf(v)));
        redis.opsForHash().putAll(key, toStore);
        redis.expire(key, cartTtl);
    }

    public void setCart(String userId, Map<String, Integer> items) {
        String key = keyFor(userId);
        Map<String, String> toStore = new HashMap<>();
        items.forEach((k, v) -> toStore.put(k, String.valueOf(v)));
        redis.opsForHash().putAll(key, toStore);
        redis.expire(key, cartTtl);
    }
}

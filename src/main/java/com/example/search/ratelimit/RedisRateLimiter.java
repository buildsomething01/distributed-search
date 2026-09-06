package com.example.search.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int requestsPerMinute;

    public RedisRateLimiter(
            StringRedisTemplate redis,
            @Value("${app.rate-limit.requests-per-minute:60000}") int requestsPerMinute) {
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
    }

    public boolean allow(String tenantId) {
        long bucket = System.currentTimeMillis() / Duration.ofMinutes(1).toMillis();
        String key = "search:rate:" + tenantId + ":" + bucket;
        Long count = redis.execute(SCRIPT, List.of(key), "60");
        return count != null && count <= requestsPerMinute;
    }
}

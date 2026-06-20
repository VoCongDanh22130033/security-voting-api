package com.nlu.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisJwtService {

    private static final String BLACKLIST_PREFIX = "blacklist:jwt:";

    @Autowired
    private StringRedisTemplate redis;

    /** Thêm token vào blacklist với TTL bằng thời gian còn lại của token */
    public void blacklistToken(String token, long remainingTtlMs) {
        if (remainingTtlMs <= 0) return;
        redis.opsForValue().set(
            BLACKLIST_PREFIX + token, "1",
            remainingTtlMs, TimeUnit.MILLISECONDS
        );
        log.info(">>> [Redis] JWT đã được blacklist");
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + token));
    }
}

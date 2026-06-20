package com.nlu.voterservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisOtpService {

    private static final String OTP_KEY_PREFIX = "otp:voter:";
    private static final String RATE_LIMIT_PREFIX = "ratelimit:otp:voter:";
    private static final long OTP_TTL_MINUTES = 5;
    private static final long RATE_LIMIT_SECONDS = 60;

    @Autowired
    private StringRedisTemplate redis;

    public void saveOtp(String email, String otp) {
        redis.opsForValue().set(OTP_KEY_PREFIX + email, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        log.info(">>> [Redis] Đã lưu OTP cho: {}", email);
    }

    public String getOtp(String email) {
        return redis.opsForValue().get(OTP_KEY_PREFIX + email);
    }

    public void deleteOtp(String email) {
        redis.delete(OTP_KEY_PREFIX + email);
        log.info(">>> [Redis] Đã xóa OTP cho: {}", email);
    }

    /** Trả về true nếu được phép gửi, false nếu bị rate limit */
    public boolean checkAndSetRateLimit(String email) {
        String key = RATE_LIMIT_PREFIX + email;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }
}

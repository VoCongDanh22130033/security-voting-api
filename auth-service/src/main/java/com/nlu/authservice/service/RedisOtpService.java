package com.nlu.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisOtpService {

    private static final String OTP_KEY_PREFIX = "otp:auth:";
    private static final long OTP_TTL_MINUTES = 15;

    @Autowired
    private StringRedisTemplate redis;

    public void saveOtp(String email, String otp) {
        redis.opsForValue().set(OTP_KEY_PREFIX + email, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        log.info(">>> [Redis] Đã lưu OTP xác thực cho: {}", email);
    }

    public String getOtp(String email) {
        return redis.opsForValue().get(OTP_KEY_PREFIX + email);
    }

    public void deleteOtp(String email) {
        redis.delete(OTP_KEY_PREFIX + email);
    }
}

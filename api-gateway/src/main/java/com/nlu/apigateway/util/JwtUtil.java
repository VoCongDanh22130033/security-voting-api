package com.nlu.apigateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  @Value("${jwt.secret}")
  private String secret;

  private SecretKey getSignKey() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public Claims extractAllClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSignKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public String extractUsername(String token) {
    return extractAllClaims(token).getSubject();
  }
}
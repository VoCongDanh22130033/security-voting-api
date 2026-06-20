package com.nlu.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  @Value("${jwt.secret}")
  private String secret;

  private Key getSignKey() {
    return Keys.hmacShaKeyFor(secret.getBytes());
  }

  public String generateToken(String email, String role) {
    return Jwts.builder()
        .setSubject(email)
        .claim("role", role)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 ngày
        .signWith(getSignKey())
        .compact();
  }

  public String extractEmail(String token) {
    return extractAllClaims(token).getSubject();
  }

  public String extractRole(String token) {
    return extractAllClaims(token).get("role", String.class);
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser()
        .setSigningKey(getSignKey())
        .parseClaimsJws(token)
        .getBody();
  }

  public boolean isTokenExpired(String token) {
    return extractAllClaims(token).getExpiration().before(new Date());
  }

  /** Trả về số ms còn lại trước khi token hết hạn (0 nếu đã hết hạn) */
  public long getRemainingTtlMs(String token) {
    Date expiration = extractAllClaims(token).getExpiration();
    long remaining = expiration.getTime() - System.currentTimeMillis();
    return Math.max(0, remaining);
  }

  public boolean isValidToken(String token, String email) {
    final String extractedEmail = extractEmail(token);
    return extractedEmail.equals(email) && !isTokenExpired(token);
  }
}

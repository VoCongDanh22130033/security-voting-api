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

  // 🔥 Tạo key 1 lần (tối ưu)
  private Key getSignKey() {
    return Keys.hmacShaKeyFor(secret.getBytes());
  }

  // ✅ Generate token
  public String generateToken(String username, String role) {
    return Jwts.builder()
        .setSubject(username)
        .claim("role", role)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 ngày
        .signWith(getSignKey())
        .compact();
  }

  // ✅ Extract username
  public String extractUsername(String token) {
    return extractAllClaims(token).getSubject();
  }

  // ✅ Extract role
  public String extractRole(String token) {
    return extractAllClaims(token).get("role", String.class);
  }

  // 🔥 Parse token (fix lỗi parserBuilder)
  private Claims extractAllClaims(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(getSignKey())
        .build()
        .parseClaimsJws(token)
        .getBody();
  }

  // ✅ Check hết hạn
  public boolean isTokenExpired(String token) {
    return extractAllClaims(token).getExpiration().before(new Date());
  }

  // ✅ Validate token
  public boolean isValidToken(String token, String username) {
    final String extractedUsername = extractUsername(token);
    return (extractedUsername.equals(username) && !isTokenExpired(token));
  }
}
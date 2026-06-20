package com.nlu.authservice.controller;

import com.nlu.authservice.dto.CreateModeratorRequest;
import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.service.AuthService;
import com.nlu.authservice.service.JwtService;
import com.nlu.authservice.service.KafkaProducerService;
import com.nlu.authservice.service.RedisJwtService;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  @Autowired
  private AuthService authService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private KafkaProducerService auditLogger;

  @Autowired
  private RedisJwtService redisJwtService;

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    auditLogger.sendAuditEvent(request.getEmail(), "VOTER_REGISTER_DISABLED", "Tự đăng ký tài khoản cử tri bị vô hiệu hóa");
    return ResponseEntity.badRequest().body(Map.of(
        "error", "Hệ thống không còn hỗ trợ đăng ký tài khoản cử tri. Vui lòng tham gia bầu cử bằng link mời trong email."
    ));
  }

  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    try {
      User user = authService.loginReturnUser(request);
      String roles = user.getRoles().stream()
          .map(Role::getName)
          .collect(Collectors.joining(","));
      String token = jwtService.generateToken(user.getEmail(), roles);
      LoginResponse response = authService.loginWithDetails(request, token);
      auditLogger.sendAuditEvent(request.getEmail(), "USER_LOGIN_SUCCESS", "Đăng nhập thành công");
      return response;
    } catch (Exception e) {
      auditLogger.sendAuditEvent(request.getEmail(), "USER_LOGIN_FAILED", "Đăng nhập thất bại: " + e.getMessage());
      throw e;
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();
      long ttl = jwtService.getRemainingTtlMs(token);
      redisJwtService.blacklistToken(token, ttl);
    }
    auditLogger.sendAuditEvent(userEmail != null ? userEmail : "unknown", "USER_LOGOUT", "Đăng xuất thành công");
    return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
  }

  @PostMapping("/verify-email")
  public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
    String token = request.get("token");
    String email = request.get("email");
    if (email == null || email.isBlank()) {
      return ResponseEntity.badRequest().body("Thiếu email.");
    }
    boolean isVerified = authService.verifyEmail(token, email);
    if (isVerified) {
      auditLogger.sendAuditEvent(email, "EMAIL_VERIFIED", "Xác thực email thành công");
      return ResponseEntity.ok("Xác thực tài khoản thành công.");
    }
    return ResponseEntity.badRequest().body("Mã xác thực không hợp lệ hoặc đã hết hạn.");
  }

  @PostMapping("/admin/create-moderator")
  public ResponseEntity<?> createModerator(
      @RequestHeader(value = "X-User-Email", required = false) String actorEmail,
      @RequestHeader(value = "Authorization", required = false) String token,
      @RequestBody CreateModeratorRequest request) {
    try {
      if (token == null || token.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Thiếu token xác thực. Vui lòng đăng nhập lại."));
      }

      String result = authService.createModerator(request, "ROLE_ADMIN");
      auditLogger.sendAuditEvent(
          actorEmail != null ? actorEmail : "admin",
          "MODERATOR_CREATED",
          "Tạo tài khoản quản trị viên: " + request.getEmail()
      );
      return ResponseEntity.ok(Map.of("message", result));
    } catch (Exception e) {
      auditLogger.sendAuditEvent(
          actorEmail != null ? actorEmail : "admin",
          "MODERATOR_CREATE_FAILED",
          "Tạo quản trị viên thất bại (" + request.getEmail() + "): " + e.getMessage()
      );
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}

package com.nlu.authservice.controller;

import com.nlu.authservice.dto.CreateModeratorRequest;
import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.service.AuthService;
import com.nlu.authservice.service.JwtService;
import com.nlu.authservice.service.KafkaProducerService;
import java.util.Map;
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

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    try {
      String result = authService.register(request);
      auditLogger.sendAuditEvent(request.getEmail(), "USER_REGISTERED", "User registered successfully");
      return ResponseEntity.ok(Map.of("message", result));
    } catch (Exception e) {
      auditLogger.sendAuditEvent(request.getEmail(), "USER_REGISTER_FAILED", "Registration failed: " + e.getMessage());
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    try {
      User user = authService.loginReturnUser(request);
      String token = jwtService.generateToken(user.getEmail(), user.getRoles().toString());
      LoginResponse response = authService.loginWithDetails(request, token);
      auditLogger.sendAuditEvent(request.getEmail(), "USER_LOGIN_SUCCESS", "User logged in successfully");
      return response;
    } catch (Exception e) {
      auditLogger.sendAuditEvent(request.getEmail(), "USER_LOGIN_FAILED", "Login failed: " + e.getMessage());
      throw e;
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(@RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    auditLogger.sendAuditEvent(userEmail != null ? userEmail : "unknown", "USER_LOGOUT", "User logged out");
    return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
  }

  @PostMapping("/verify-email")
  public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
    String token = request.get("token");
    boolean isVerified = authService.verifyEmail(token);
    if (isVerified) {
      auditLogger.sendAuditEvent("unknown", "EMAIL_VERIFIED", "User email verified");
      return ResponseEntity.ok("Xac thuc tai khoan thanh cong.");
    }
    return ResponseEntity.badRequest().body("Ma xac thuc khong hop le hoac da het han.");
  }

  @PostMapping("/admin/create-moderator")
  public ResponseEntity<?> createModerator(
      @RequestHeader(value = "X-User-Email", required = false) String actorEmail,
      @RequestHeader(value = "Authorization", required = false) String token,
      @RequestBody CreateModeratorRequest request) {
    try {
      if (token == null || token.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Missing authorization token"));
      }

      String result = authService.createModerator(request, "ROLE_ADMIN");
      auditLogger.sendAuditEvent(
          actorEmail != null ? actorEmail : "admin",
          "MODERATOR_CREATED",
          "Created moderator account for: " + request.getEmail()
      );
      return ResponseEntity.ok(Map.of("message", result));
    } catch (Exception e) {
      auditLogger.sendAuditEvent(
          actorEmail != null ? actorEmail : "admin",
          "MODERATOR_CREATE_FAILED",
          "Failed creating moderator " + request.getEmail() + ": " + e.getMessage()
      );
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}

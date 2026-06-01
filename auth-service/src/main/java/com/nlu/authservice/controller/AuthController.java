package com.nlu.authservice.controller;

import com.nlu.authservice.dto.CreateModeratorRequest;
import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.service.AuthService;
import com.nlu.authservice.service.JwtService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  @Autowired
  private AuthService authService;

  @Autowired
  private JwtService jwtService;
  //Đăng Ký
  @PostMapping("/register")
  public String register(@RequestBody RegisterRequest request) {
    return authService.register(request);
  }
  // đăng nhập
  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    User user = authService.loginReturnUser(request);
    String token = jwtService.generateToken(
        user.getEmail(),
        user.getRoles().toString()
    );
    return authService.loginWithDetails(request, token);
  }
  // xác thực email
  @PostMapping("/verify-email")
  public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
    String token = request.get("token");
    boolean isVerified = authService.verifyEmail(token);

    if (isVerified) {
      return ResponseEntity.ok("Xác thực tài khoản thành công!");
    } else {
      return ResponseEntity.badRequest().body("Mã xác thực không hợp lệ hoặc đã hết hạn.");
    }
  }

  // --- SUPER_ADMIN: Tạo tài khoản chủ trì bầu cử ---
  @PostMapping("/admin/create-moderator")
  public ResponseEntity<?> createModerator(
      @RequestHeader(value = "Authorization", required = false) String token,
      @RequestBody CreateModeratorRequest request) {
    try {
      if (token == null || token.isEmpty()) {
        return ResponseEntity.badRequest().body("Lỗi: Thiếu token xác thực!");
      }


      String result = authService.createModerator(request, "ROLE_ADMIN");
      return ResponseEntity.ok(Map.of("message", result));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

//  @PostMapping("/admin/lock-account/{userId}")
//  public ResponseEntity<?> lockAccount(
//      @RequestHeader(value = "Authorization", required = false) String token,
//      @PathVariable Long userId) {
//    try {
//      if (token == null || token.isEmpty()) {
//        return ResponseEntity.badRequest().body("Lỗi: Thiếu token xác thực!");
//      }
//
//      String result = authService.lockUser(userId);
//      return ResponseEntity.ok(Map.of("message", result));
//    } catch (Exception e) {
//      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
//    }
//  }

//  @PostMapping("/admin/unlock-account/{userId}")
//  public ResponseEntity<?> unlockAccount(
//      @RequestHeader(value = "Authorization", required = false) String token,
//      @PathVariable Long userId) {
//    try {
//      if (token == null || token.isEmpty()) {
//        return ResponseEntity.badRequest().body("Lỗi: Thiếu token xác thực!");
//      }
//
//      // TODO: Kiểm tra token có phải SUPER_ADMIN không
//      String result = authService.unlockUser(userId);
//      return ResponseEntity.ok(Map.of("message", result));
//    } catch (Exception e) {
//      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
//    }
//  }
}
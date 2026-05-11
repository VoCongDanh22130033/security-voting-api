package com.nlu.authservice.controller;

import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.service.AuthService;
import com.nlu.authservice.service.JwtService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
}
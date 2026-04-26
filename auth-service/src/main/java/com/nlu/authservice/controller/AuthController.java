package com.nlu.authservice.controller;


import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.service.AuthService;
import com.nlu.authservice.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
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

  @PostMapping("/register")
  public String register(@RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    User user = authService.loginReturnUser(request);

    String token = jwtService.generateToken(
        user.getEmail(),
        user.getRoles().toString()
    );

    return new LoginResponse(token);
  }
}
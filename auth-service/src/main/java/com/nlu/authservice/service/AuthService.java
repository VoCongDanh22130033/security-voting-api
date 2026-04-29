package com.nlu.authservice.service;

import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.repository.RoleRepository;
import com.nlu.authservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RoleRepository roleRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;


  public String register(RegisterRequest request) {
    User user = new User();
    user.setUsername(request.getUsername());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());

    // Gán quyền mặc định là ROLE_VOTER
    Role role = roleRepository.findByName("ROLE_VOTER")
        .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy quyền ROLE_VOTER trong hệ thống."));
    user.setRoles(Set.of(role));

    userRepository.save(user);
    return "Register success";
  }


  public String login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new RuntimeException("User not found"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new RuntimeException("Invalid password");
    }

    return user.getUsername();
  }

  public User loginReturnUser(LoginRequest request) {
    System.out.println(">>> Đang đăng nhập với email: [" + request.getEmail() + "]");

    return userRepository.findByEmail(request.getEmail().trim())
        .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));
  }

  public LoginResponse loginWithDetails(LoginRequest request, String token) {
    User user = userRepository.findByEmail(request.getEmail().trim())
        .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new RuntimeException("Mật khẩu không chính xác!");
    }

    Set<String> roles = user.getRoles().stream()
        .map(Role::getName)
        .collect(java.util.stream.Collectors.toSet());

    return new LoginResponse(token, user.getUsername(), user.getEmail(), roles ,user.getId());
  }
}
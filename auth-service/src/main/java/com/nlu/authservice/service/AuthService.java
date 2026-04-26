package com.nlu.authservice.service;

import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.repository.RoleRepository;
import com.nlu.authservice.repository.UserRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    Role role = roleRepository.findByName("ROLE_VOTER").orElseThrow();
    user.setRoles(Set.of(role));

    userRepository.save(user);

    return "Register success";
  }

  public String login(LoginRequest request) {

    User user = (User) userRepository.findByEmail(request.getEmail())
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
}
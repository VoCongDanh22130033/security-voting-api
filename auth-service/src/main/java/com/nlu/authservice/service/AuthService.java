package com.nlu.authservice.service;

import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.entity.VerificationToken;
import com.nlu.authservice.entity.Voter;
import com.nlu.authservice.repository.RoleRepository;
import com.nlu.authservice.repository.UserRepository;
import com.nlu.authservice.repository.VerificationTokenRepository;
import com.nlu.authservice.repository.VoterRepository;
import java.time.LocalDateTime;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class AuthService {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RoleRepository roleRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private VoterRepository voterRepository;


  @Autowired
  private VerificationTokenRepository tokenRepository;

  @Autowired
  private JavaMailSender mailSender;
  public String register(RegisterRequest request) {
    // 1. Kiểm tra email tồn tại
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new RuntimeException("Lỗi: Email đã được sử dụng!");
    }

    // 2. Tạo User mới (mặc định chưa xác thực)
    User user = new User();
    user.setFullName(request.getFullName());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());
    user.setVerified(false);
    User savedUser = userRepository.save(user);
    Voter voter = new Voter();
    voter.setUser(savedUser);
    voter.setFullName(savedUser.getFullName());
    voter.setVerified(false);
    voterRepository.save(voter);

    Role role = roleRepository.findByName("ROLE_VOTER")
        .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy ROLE_VOTER."));
    user.setRoles(Set.of(role));

    // 3. Tạo Token và Gửi Email xác thực
    try {
      sendVerificationEmail(savedUser);
    } catch (Exception e) {
      // Log lỗi nếu cấu hình SMTP trong yaml sai
      System.out.println("Lỗi gửi mail: " + e.getMessage());
    }

    return "Đăng ký thành công! Vui lòng kiểm tra email để xác thực tài khoản.";
  }

  public User loginReturnUser(LoginRequest request) {
    System.out.println("Đăng nhập với email: [" + request.getEmail() + "]");
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
    return new LoginResponse(
        token,
        user.getFullName(),
        user.getEmail(),
        roles,
        user.getId(),
        user.getImage_url()
    );
  }
  // gửi mã xác thực
  public void sendVerificationEmail(User user) {
    String token = String.valueOf(new Random().nextInt(899999) + 100000);
    VerificationToken vToken = new VerificationToken(user, token);
    tokenRepository.save(vToken);

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(user.getEmail());
    message.setSubject("Xác thực tài khoản E-Voting");
    message.setText("Mã xác thực của bạn là: " + token);
    mailSender.send(message);
  }
  public boolean verifyEmail(String token) {
    VerificationToken vToken = tokenRepository.findByToken(token)
        .orElseThrow(() -> new RuntimeException("Mã không hợp lệ"));

    if (vToken.getExpiryDate().isBefore(LocalDateTime.now())) {
      throw new RuntimeException("Mã đã hết hạn");
    }

    User user = vToken.getUser();
    user.setVerified(true);
    userRepository.save(user);
    tokenRepository.delete(vToken);
    return true;
  }

}
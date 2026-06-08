package com.nlu.authservice.service;

import com.nlu.authservice.dto.CreateModeratorRequest;
import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.Employee;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.entity.VerificationToken;
import com.nlu.authservice.entity.Voter;
import com.nlu.authservice.repository.EmployeeRepository;
import com.nlu.authservice.repository.RoleRepository;
import com.nlu.authservice.repository.UserRepository;
import com.nlu.authservice.repository.VerificationTokenRepository;
import com.nlu.authservice.repository.VoterRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

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

  @Autowired
  private EmployeeRepository employeeRepository;

  @Transactional
  public String register(RegisterRequest request) {
    // 1. Kiểm tra email tồn tại trong bảng users
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new RuntimeException("Lỗi: Email đã được sử dụng cho một tài khoản khác!");
    }

    // 2. Kiểm tra email có trong danh sách nhân viên hợp lệ (employees) không
    Optional<Employee> employeeOpt = employeeRepository.findByEmail(request.getEmail());
    if (employeeOpt.isEmpty()) {
      throw new RuntimeException("Lỗi: Email không nằm trong danh sách nhân sự được cấp phép tham gia hệ thống.");
    }

    Employee employee = employeeOpt.get();
    if (!employee.isActive()) {
      throw new RuntimeException("Lỗi: Tài khoản nhân sự này đã bị vô hiệu hóa.");
    }

    // 3. Tạo User mới và liên kết với Employee
    User user = new User();
    user.setFullName(request.getFullName() != null && !request.getFullName().isEmpty() ? request.getFullName() : employee.getFullName());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());
    user.setVerified(false);
    user.setEmployee(employee); // Liên kết User -> Employee
    User savedUser = userRepository.save(user);

    // 4. Tạo Voter
    Voter voter = new Voter();
    voter.setUser(savedUser);
    voter.setFullName(savedUser.getFullName());
    voter.setVerified(false);
    voterRepository.save(voter);

    // 5. Gán quyền ROLE_VOTER
    Role role = roleRepository.findByName("ROLE_VOTER")
        .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy ROLE_VOTER."));
    user.setRoles(Set.of(role));

    // 6. Tạo Token và Gửi Email xác thực
    try {
      sendVerificationEmail(savedUser);
    } catch (Exception e) {
      System.out.println("Lỗi gửi mail: " + e.getMessage());
    }

    return "Đăng ký thành công! Vui lòng kiểm tra email để xác thực tài khoản.";
  }

  public User loginReturnUser(LoginRequest request) {
    System.out.println("Đăng nhập với email: [" + request.getEmail() + "]");

    User user = userRepository.findByEmail(request.getEmail().trim())
        .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));
    if (user.getIsLock() != null && user.getIsLock() == 1) {
      throw new RuntimeException("Tài khoản của bạn đã bị khóa bởi ban quản trị hệ thống!");
    }

    boolean isVoterOnly = user.getRoles() != null
        && user.getRoles().stream().anyMatch(role -> "ROLE_VOTER".equals(role.getName()))
        && user.getRoles().stream().noneMatch(role -> "ROLE_ADMIN".equals(role.getName()) || "ROLE_ORGANIZER".equals(role.getName()));
    if (isVoterOnly) {
      throw new RuntimeException("Tai khoan cu tri khong dang nhap bang mat khau. Vui long tham gia bau cu bang link moi trong email.");
    }

    return user;
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

  @Transactional
  public String createModerator(CreateModeratorRequest request, String superAdminRole) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new RuntimeException("Lỗi: Email đã được sử dụng!");
    }
    Role moderatorRole = roleRepository.findByName("ROLE_ORGANIZER")
        .orElseThrow(() -> new RuntimeException("Lỗi hệ thống: Không tìm thấy ROLE_ORGANIZER trong bảng roles!"));

    User user = new User();
    user.setFullName(request.getFullName());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setEmail(request.getEmail());
    user.setPhone(request.getPhone());
    user.setVerified(true);
    user.setIsLock(0);

    user.setRoles(Set.of(moderatorRole));

    userRepository.save(user);

    return "Tạo tài khoản chủ trì bầu cử thành công! Email: " + request.getEmail();
  }
}

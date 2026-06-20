package com.nlu.authservice.service;

import com.nlu.authservice.dto.CreateModeratorRequest;
import com.nlu.authservice.dto.LoginRequest;
import com.nlu.authservice.dto.LoginResponse;
import com.nlu.authservice.dto.RegisterRequest;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.repository.RoleRepository;
import com.nlu.authservice.repository.UserRepository;
import java.util.Random;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RoleRepository roleRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JavaMailSender mailSender;

  @Autowired
  private RedisOtpService redisOtpService;

  @Value("${app.frontend-url:http://localhost:5173}")
  private String frontendUrl;

  @Transactional
  public String register(RegisterRequest request) {
    throw new RuntimeException("Hệ thống không còn hỗ trợ đăng ký tài khoản cử tri. Vui lòng tham gia bầu cử bằng link mời trong email.");
  }

  public User loginReturnUser(LoginRequest request) {
    log.info("Dang nhap voi email: [{}]", request.getEmail());

    User user = userRepository.findByEmail(request.getEmail().trim())
        .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));
    if (user.getIsLock() != null && user.getIsLock() == 1) {
      throw new RuntimeException("Tài khoản của bản đã bị khóa bởi quản trị viên hệ thống!");
    }

    boolean isVoterOnly = user.getRoles() != null
        && user.getRoles().stream().anyMatch(role -> "ROLE_VOTER".equals(role.getName()))
        && user.getRoles().stream().noneMatch(role -> "ROLE_ADMIN".equals(role.getName()) || "ROLE_ORGANIZER".equals(role.getName()));
    if (isVoterOnly) {
      throw new RuntimeException("Tài khoản cử tri không đăng nhập bằng mật khẩu. Vui lòng tham gia bầu cử bằng link mời trong email.");
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

  public void sendVerificationEmail(User user) {
    String otp = String.valueOf(new Random().nextInt(899999) + 100000);
    redisOtpService.saveOtp(user.getEmail(), otp);
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(user.getEmail());
    message.setSubject("Xác thực tài khoản E-Voting");
    message.setText("Mã xác thực của bạn là: " + otp);
    mailSender.send(message);
  }

  @Transactional
  public boolean verifyEmail(String token, String email) {
    String stored = redisOtpService.getOtp(email);
    if (stored == null) {
      throw new RuntimeException("Mã đã hết hạn.");
    }
    if (!stored.equals(token)) {
      throw new RuntimeException("Mã không hợp lệ.");
    }
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại."));
    user.setVerified(true);
    userRepository.save(user);
    redisOtpService.deleteOtp(email);
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
    sendOrganizerWelcomeEmail(request.getEmail(), request.getFullName(), request.getPassword());

    return "Tạo tài khoản chủ trì bầu cử thành công! Email: " + request.getEmail();
  }

  private void sendOrganizerWelcomeEmail(String email, String fullName, String rawPassword) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setTo(email);
      helper.setSubject("Bạn đã được bổ nhiệm làm Chủ trì Bầu cử – E-Voting");
      String loginUrl = frontendUrl + "/login";
      String html = """
          <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden">
            <div style="background:linear-gradient(135deg,#1e3a8a,#2563eb);padding:32px 24px;text-align:center">
              <h1 style="color:#fff;margin:0;font-size:22px">🗳️ E-Voting System</h1>
              <p style="color:#bfdbfe;margin:8px 0 0">Hệ thống Bầu cử Điện tử Bảo mật</p>
            </div>
            <div style="padding:32px 24px">
              <p style="color:#0f172a;font-size:16px">Xin chào <strong>%s</strong>,</p>
              <p style="color:#475569">Quản trị viên hệ thống đã bổ nhiệm bạn làm <strong style="color:#2563eb">Chủ trì Bầu cử</strong>. Dưới đây là thông tin đăng nhập của bạn:</p>
              <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin:20px 0">
                <table style="width:100%%">
                  <tr>
                    <td style="color:#64748b;padding:6px 0;width:120px">Email:</td>
                    <td style="color:#0f172a;font-weight:600">%s</td>
                  </tr>
                  <tr>
                    <td style="color:#64748b;padding:6px 0">Mật khẩu:</td>
                    <td style="color:#0f172a;font-weight:600;font-family:monospace">%s</td>
                  </tr>
                </table>
              </div>
              <p style="color:#dc2626;font-size:13px">⚠️ Vui lòng đổi mật khẩu sau lần đăng nhập đầu tiên để bảo mật tài khoản.</p>
              <p style="color:#475569">Với tư cách Chủ trì, bạn có thể tạo và quản lý các cuộc bầu cử, thêm ứng viên và theo dõi kết quả trực tiếp trên hệ thống.</p>
              <div style="text-align:center;margin:28px 0">
                <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#1e3a8a,#2563eb);color:#fff;text-decoration:none;padding:14px 36px;border-radius:8px;font-weight:700;font-size:15px;letter-spacing:0.3px">
                  🔐 Đăng nhập ngay
                </a>
              </div>
              <p style="color:#94a3b8;font-size:12px;text-align:center">Hoặc truy cập: <a href="%s" style="color:#2563eb">%s</a></p>
            </div>
            <div style="background:#f1f5f9;padding:16px 24px;text-align:center">
              <p style="color:#94a3b8;font-size:12px;margin:0">© E-Voting System – Email này được gửi tự động, vui lòng không trả lời.</p>
            </div>
          </div>
          """.formatted(fullName, email, rawPassword, loginUrl, loginUrl, loginUrl);
      helper.setText(html, true);
      mailSender.send(message);
      log.info("Đã gửi email bổ nhiệm chủ trì tới: {}", email);
    } catch (Exception e) {
      log.error("Không thể gửi email bổ nhiệm tới {}: {}", email, e.getMessage());
    }
  }
}

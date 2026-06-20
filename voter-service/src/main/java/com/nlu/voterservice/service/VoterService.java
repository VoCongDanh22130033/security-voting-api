package com.nlu.voterservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.nlu.voterservice.dto.ResetPasswordWithOtpRequest;
import com.nlu.voterservice.dto.VoterResponse;
import com.nlu.voterservice.entity.User;
import com.nlu.voterservice.entity.Voter;
import com.nlu.voterservice.dto.UpdateProfileRequest;
import com.nlu.voterservice.repository.VoterRepository;
import com.nlu.voterservice.repository.UserRepository;
import com.nlu.voterservice.repository.RoleRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class VoterService {

  @Autowired
  private VoterRepository voterRepository;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private RoleRepository roleRepository;
  @Autowired
  private Cloudinary cloudinary;

  @Autowired
  private JavaMailSender mailSender;

  @Autowired
  private RedisOtpService redisOtpService;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public java.util.Map<String, Object> getProfileByEmail(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản: " + email));

    String roleName = user.getRoles().stream()
        .findFirst()
        .map(r -> r.getName())
        .orElse("ROLE_VOTER");

    java.util.Map<String, Object> result = new java.util.HashMap<>();
    result.put("fullName", user.getFullName());
    result.put("email", user.getEmail());
    result.put("phone", user.getPhone());
    result.put("image_url", user.getImage_url());
    result.put("role", roleName);

    // Nếu là cử tri thì lấy thêm citizenId từ bảng voters
    voterRepository.findByEmail(email).ifPresent(v -> {
      result.put("citizenId", v.getCitizenId());
    });

    return result;
  }

  public VoterResponse getProfile(String email) {
    Voter voter = voterRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Cử tri không tồn tại với email: " + email));

    String roleName = voter.getUser().getRoles().stream()
        .findFirst()
        .map(role -> role.getName())
        .orElse("ROLE_VOTER");

    return new VoterResponse(
        voter.getUser().getEmail(),
        roleName
    );
  }

  public Voter findByEmail(String email) {
    return voterRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với email: " + email));
  }



  public java.util.List<Voter> listVotersByRole(Long roleId) {
    return voterRepository.findByRoleId(roleId);
  }

  @Transactional
  public Voter changeUserRole(Long userId, Long roleId) {
    Voter voter = voterRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với id: " + userId));

    com.nlu.voterservice.entity.User user = voter.getUser();
    if (user == null) {
      throw new RuntimeException("Không tìm thấy tài khoản người dùng liên kết với cử tri này!");
    }
    com.nlu.voterservice.entity.Role role = roleRepository.findById(roleId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy role với id: " + roleId));
    user.setRoles(java.util.Set.of(role));
    voterRepository.saveAndFlush(voter);
    return voter;
  }


  @Transactional
  public java.util.Map<String, Object> updateProfile(String email, UpdateProfileRequest request) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản: " + email));

    if (request.getFullName() != null) {
      user.setFullName(request.getFullName());
    }

    if (request.getPhone() != null) {
      user.setPhone(String.valueOf(request.getPhone()));
    }

    if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
      try {
        log.info(">>> [BE] Đang tải ảnh lên Cloudinary cho: {}", email);
        Map uploadResult = cloudinary.uploader().upload(
            request.getAvatar().getBytes(),
            ObjectUtils.asMap("folder", "e_voting_avatars", "resource_type", "image")
        );
        String secureUrl = (String) uploadResult.get("secure_url");
        user.setImage_url(secureUrl);
        log.info(">>> [BE] Cloudinary OK. URL: {}", secureUrl);
      } catch (Exception e) {
        throw new RuntimeException("Thất bại khi upload ảnh: " + e.getMessage());
      }
    }

    userRepository.save(user);

    // Nếu là cử tri thì cập nhật fullName trong voters luôn
    voterRepository.findByEmail(email).ifPresent(voter -> {
      if (request.getFullName() != null) voter.setFullName(request.getFullName());
      voterRepository.save(voter);
    });

    java.util.Map<String, Object> result = new java.util.HashMap<>();
    result.put("fullName", user.getFullName());
    result.put("email", user.getEmail());
    result.put("phone", user.getPhone());
    result.put("image_url", user.getImage_url());
    return result;
  }


  public void sendOtpForgotPassword(String email) {
    userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống!"));

    if (!redisOtpService.checkAndSetRateLimit(email)) {
      throw new RuntimeException("Vui lòng chờ 60 giây trước khi yêu cầu OTP mới!");
    }

    String otp = String.format("%06d", new java.util.Random().nextInt(999999));
    redisOtpService.saveOtp(email, otp);

    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(email);
      message.setSubject("[E-Voting System] Mã OTP Đặt Lại Mật Khẩu");
      message.setText("Mã OTP của bạn là: " + otp + "\nMã này có hiệu lực trong vòng 5 phút.");
      mailSender.send(message);
      log.info(">>> [BE] Đã gửi mail OTP tới: {}", email);
    } catch (Exception e) {
      log.error(">>> [BE] Lỗi khi gửi mail: {}", e.getMessage());
      throw new RuntimeException("Không thể gửi email OTP. Vui lòng thử lại sau.");
    }
  }

  public void verifyOtp(String email, String otpCode) {
    String stored = redisOtpService.getOtp(email);
    if (stored == null) {
      throw new RuntimeException("Mã OTP đã hết hiệu lực hoặc không tồn tại!");
    }
    if (!stored.equals(otpCode)) {
      throw new RuntimeException("Mã OTP không chính xác!");
    }
  }

  @Transactional
  public void resetPasswordWithOtp(ResetPasswordWithOtpRequest request) {
    String stored = redisOtpService.getOtp(request.getEmail());
    if (stored == null) {
      throw new RuntimeException("Mã OTP đã hết hiệu lực!");
    }
    if (!stored.equals(request.getOtpCode())) {
      throw new RuntimeException("Mã OTP không chính xác!");
    }

    User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản!"));
    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);

    redisOtpService.deleteOtp(request.getEmail());
    log.info(">>> [BE] Đổi mật khẩu thành công cho: {}", request.getEmail());
  }

  public java.util.Map<String, Object> getVoterById(Long userId) {
    String sql = "SELECT u.id as id, u.full_name as fullName, u.email as email, " +
        "u.phone as phone, u.is_lock as isLock " +
        "FROM users u " +
        "WHERE u.id = ?1";

    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
    query.setParameter(1, userId);

    query.unwrap(org.hibernate.query.NativeQuery.class)
        .setResultTransformer(org.hibernate.transform.Transformers.ALIAS_TO_ENTITY_MAP);

    java.util.List<java.util.Map<String, Object>> result = query.getResultList();
    if (result.isEmpty()) {
      throw new RuntimeException("Không tìm thấy thông tin tài khoản với id: " + userId);
    }
    return result.get(0);
  }

//  @Transactional
//  public void lockAccount(Long userId) {
//    Voter voter = voterRepository.findById(userId)
//        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với id: " + userId));
//    User user = voter.getUser();
//    if (user == null) {
//      throw new RuntimeException("Không tìm thấy tài khoản người dùng liên kết với cử tri này!");
//    }
//
//    user.setIsLock(1);
//    voterRepository.saveAndFlush(voter);
//  }
//  @Transactional
//  public void unlockAccount(Long userId) {
//    Voter voter = voterRepository.findById(userId)
//        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với id: " + userId));
//
//    User user = voter.getUser();
//    if (user == null) {
//      throw new RuntimeException("Không tìm thấy tài khoản người dùng liên kết với cử tri này!");
//    }
//
//    if (user.getIsLock() == null || user.getIsLock() == 0) {
//      throw new RuntimeException("Tài khoản này không bị khóa!");
//    }
//    user.setIsLock(0);
//
//    voterRepository.saveAndFlush(voter);
//  }
// Tìm và thay thế hoàn toàn 2 hàm lockAccount và unlockAccount trong VoterService.java:

  @Transactional
  public void lockAccount(Long userId) {
    // Thay vì dùng voterRepository, ta dùng Native Query cập nhật thẳng trường is_lock trong bảng users
    String sql = "UPDATE users SET is_lock = 1 WHERE id = ?1";
    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
    query.setParameter(1, userId);

    int rowsUpdated = query.executeUpdate();
    if (rowsUpdated == 0) {
      throw new RuntimeException("Không tìm thấy tài khoản người dùng với id: " + userId);
    }
    log.info(">>> [BE] Đã khóa tài khoản thành công cho User ID: {}", userId);
  }

  @Transactional
  public void unlockAccount(Long userId) {
    String sql = "UPDATE users SET is_lock = 0 WHERE id = ?1";
    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
    query.setParameter(1, userId);

    int rowsUpdated = query.executeUpdate();
    if (rowsUpdated == 0) {
      throw new RuntimeException("Không tìm thấy tài khoản người dùng với id: " + userId);
    }
    log.info(">>> [BE] Đã mở khóa tài khoản thành công cho User ID: {}", userId);
  }

  @Transactional
  public void deleteUser(Long userId) {
    String checkSql = "SELECT COUNT(*) FROM users WHERE id = ?1";
    jakarta.persistence.Query checkQuery = entityManager.createNativeQuery(checkSql);
    checkQuery.setParameter(1, userId);
    Number count = (Number) checkQuery.getSingleResult();
    if (count.intValue() == 0) {
      throw new RuntimeException("Không tìm thấy tài khoản người dùng với id: " + userId);
    }
    entityManager.createNativeQuery("DELETE FROM user_roles WHERE user_id = ?1").setParameter(1, userId).executeUpdate();
    entityManager.createNativeQuery("DELETE FROM voters WHERE user_id = ?1").setParameter(1, userId).executeUpdate();
    entityManager.createNativeQuery("DELETE FROM verification_tokens WHERE user_id = ?1").setParameter(1, userId).executeUpdate();
    int rows = entityManager.createNativeQuery("DELETE FROM users WHERE id = ?1").setParameter(1, userId).executeUpdate();
    if (rows == 0) {
      throw new RuntimeException("Xóa tài khoản thất bại cho id: " + userId);
    }
    log.info(">>> [BE] Đã xóa tài khoản thành công cho User ID: {}", userId);
  }

  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager entityManager;
  public java.util.List<java.util.Map<String, Object>> getAllUsersByRoleId(Long roleId) {
    String sql = "SELECT u.id as id, u.full_name as fullName, u.email as email, " +
        "u.phone as phone, u.is_lock as isLock " +
        "FROM users u " +
        "JOIN user_roles ur ON u.id = ur.user_id " +
        "WHERE ur.role_id = ?1";
    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
    query.setParameter(1, roleId);
    query.unwrap(org.hibernate.query.NativeQuery.class)
        .setResultTransformer(org.hibernate.transform.Transformers.ALIAS_TO_ENTITY_MAP);
    return query.getResultList();
  }
}

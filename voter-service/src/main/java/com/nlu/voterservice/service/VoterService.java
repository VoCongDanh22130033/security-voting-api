package com.nlu.voterservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.nlu.voterservice.dto.ResetPasswordWithOtpRequest;
import com.nlu.voterservice.dto.VoterResponse;
import com.nlu.voterservice.entity.User;
import com.nlu.voterservice.entity.VerificationToken;
import com.nlu.voterservice.entity.Voter;
import com.nlu.voterservice.dto.UpdateProfileRequest;
import com.nlu.voterservice.repository.VerificationTokenRepository;
import com.nlu.voterservice.repository.VoterRepository;
import com.nlu.voterservice.repository.RoleRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoterService {

  @Autowired
  private VoterRepository voterRepository;
  @Autowired
  private RoleRepository roleRepository;
  @Autowired
  private Cloudinary cloudinary;

  @Autowired
  private VerificationTokenRepository tokenRepository;

  @Autowired
  private JavaMailSender mailSender;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
  public Voter updateProfile(String email, UpdateProfileRequest request) {
    Voter voter = voterRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với email: " + email));


    User user = voter.getUser();
    if (user == null) {
      throw new RuntimeException("Không tìm thấy tài khoản người dùng liên kết với cử tri này!");
    }

    if (request.getFullName() != null) {
      user.setFullName(request.getFullName());
      voter.setFullName(request.getFullName());
    }

    if (request.getPhone() != null) {
      user.setPhone(String.valueOf(request.getPhone()));
    }

    if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
      try {
        System.out.println(">>> [BE] Đang tải ảnh đại diện lên Cloudinary cho tài khoản: " + email);
        Map uploadResult = cloudinary.uploader().upload(
            request.getAvatar().getBytes(),
            ObjectUtils.asMap(
                "folder", "e_voting_avatars",
                "resource_type", "image"
            )
        );
        String secureUrl = (String) uploadResult.get("secure_url");
        user.setImage_url(secureUrl);
        System.out.println(">>> [BE] Đẩy Cloudinary thành công. URL: " + secureUrl);
      } catch (Exception e) {
        throw new RuntimeException("Thất bại khi xử lý upload ảnh: " + e.getMessage());
      }
    }

    System.out.println(">>> [BE] Tiến hành Flush dữ liệu xuống MySQL cho cả bảng users và voters...");

    return voterRepository.saveAndFlush(voter);
  }


public void sendOtpForgotPassword(String email) {
  Voter voter = voterRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống!"));

  User user = voter.getUser();

  String otp = String.format("%06d", new java.util.Random().nextInt(999999));

  this.saveNewOtpToken(user, otp);


  try {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("[E-Voting System] Mã OTP Đặt Lại Mật Khẩu");
    message.setText("Mã OTP của bạn là: " + otp + "\nMã này có hiệu lực trong vòng 5 phút.");

    mailSender.send(message);
    System.out.println(">>> [BE] Đã gửi mail thành công tới: " + email);
  } catch (Exception e) {
    System.err.println(">>> [BE] Lỗi khi gửi mail: " + e.getMessage());
    throw new RuntimeException("Gửi mail thất bại nhưng mã xác thực đã được tạo!");
  }
}


  @Transactional
  public void saveNewOtpToken(User user, String otp) {
    tokenRepository.deleteByUser(user);
    tokenRepository.flush();

    VerificationToken verificationToken = new VerificationToken();
    verificationToken.setToken(otp);
    verificationToken.setExpiryDate(LocalDateTime.now().plusMinutes(5));
    verificationToken.setUser(user);

    tokenRepository.saveAndFlush(verificationToken);
    System.out.println(">>> [BE] Đã COMMIT vĩnh viễn OTP vào bảng verification_token cho User ID: " + user.getId());
  }

  @Transactional
  public void resetPasswordWithOtp(ResetPasswordWithOtpRequest request) {

    VerificationToken verificationToken = (VerificationToken) tokenRepository.findByToken(request.getOtpCode())
        .orElseThrow(() -> new RuntimeException("Mã OTP không chính xác!"));

    if (!verificationToken.getUser().getEmail().equals(request.getEmail())) {
      throw new RuntimeException("Mã OTP không khớp với tài khoản yêu cầu!");
    }

    if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
      tokenRepository.delete(verificationToken);
      throw new RuntimeException("Mã OTP đã hết hiệu lực!");
    }

    User user = verificationToken.getUser();
    user.setPassword(passwordEncoder.encode(request.getNewPassword()));

    voterRepository.save(voterRepository.findByEmail(request.getEmail()).get());

    tokenRepository.delete(verificationToken);
    System.out.println(">>> [BE] Đổi mật khẩu thành công, đã dọn dẹp bảng verification_token.");
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
    System.out.println(">>> [BE] Đã khóa tài khoản thành công cho User ID: " + userId);
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
    System.out.println(">>> [BE] Đã mở khóa tài khoản thành công cho User ID: " + userId);
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
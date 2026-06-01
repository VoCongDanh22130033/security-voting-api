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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
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

  public Voter getVoterById(Long userId) {
    return voterRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với id: " + userId));
  }

  // Thay thế hàm lockAccount cũ trong VoterService.java
  @Transactional
  public void lockAccount(Long userId) {
    Voter voter = voterRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với id: " + userId));

    // Lấy ra thực thể User liên kết từ Voter[cite: 2]
    User user = voter.getUser();
    if (user == null) {
      throw new RuntimeException("Không tìm thấy tài khoản người dùng liên kết với cử tri này!");
    }

    // Cập nhật trường is_lock thành 1 (Đại diện cho trạng thái bị khóa)
    user.setIsLock(1);

    // Lưu và đồng bộ xuống Database ngay lập tức[cite: 2]
    voterRepository.saveAndFlush(voter);
  }
  @Transactional
  public Voter updateProfile(String email, UpdateProfileRequest request) {
    // 1. Tìm Voter theo email (Lệnh này đồng thời lấy ra đối tượng User tương ứng nhờ JPA Join)
    Voter voter = voterRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với email: " + email));

    // Lấy ra thực thể User liên kết từ Voter để thao tác trực tiếp với các cột trong bảng `users`
    User user = voter.getUser();
    if (user == null) {
      throw new RuntimeException("Không tìm thấy tài khoản người dùng liên kết với cử tri này!");
    }

    // 2. Cập nhật các trường thuộc bảng `users` (Email, Phone, FullName, ImageUrl gốc đều nằm ở đây)
    if (request.getFullName() != null) {
      user.setFullName(request.getFullName()); // Đảm bảo đồng bộ Họ tên sang bảng users
      voter.setFullName(request.getFullName()); // Đồng bộ sang cả bảng voters nếu bạn muốn giữ cả 2 bên
    }

    if (request.getPhone() != null) {
      user.setPhone(String.valueOf(request.getPhone())); // Ghi trực tiếp vào cột phone của bảng users
    }


    // 4. Xử lý tải ảnh lên Cloudinary và lưu vào cột image_url của bảng `users` (Theo chuẩn SQL)
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

        // ĐỔI TẠI ĐÂY: Lưu đường dẫn hình ảnh vào bảng `users` vì bảng `voters` trong SQL không có cột này!
        user.setImage_url(secureUrl);

        System.out.println(">>> [BE] Đẩy Cloudinary thành công. URL: " + secureUrl);
      } catch (Exception e) {
        throw new RuntimeException("Thất bại khi xử lý upload ảnh: " + e.getMessage());
      }
    }

    // 5. Thực hiện lưu và ép Hibernate phải đồng bộ xuống MySQL ngay lập tức
    System.out.println(">>> [BE] Tiến hành Flush dữ liệu xuống MySQL cho cả bảng users và voters...");

    // Lưu voter, nếu trong file Entity Voter.java bạn đã cấu hình cascade = CascadeType.ALL ở biến user,
    // thì lệnh dưới đây sẽ tự động lưu cả thay đổi của bảng users một cách đồng bộ.
    return voterRepository.saveAndFlush(voter);
  }

//  @Transactional
//  public void sendOtpForgotPassword(String email) {
//    Voter voter = voterRepository.findByEmail(email)
//        .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống!"));
//
//    User user = voter.getUser();
//
//    // Sinh mã OTP 6 số
//    String otp = String.format("%06d", new java.util.Random().nextInt(999999));
//
//    // --- BỔ SUNG: Xóa sạch toàn bộ token cũ của User này nếu có để tránh lỗi Duplicate entry ---
//    tokenRepository.deleteByUser(user);
//    // Ép Hibernate đẩy lệnh xóa xuống DB trước khi chèn lệnh insert mới
//    tokenRepository.flush();
//
//    // Tạo đối tượng token mới
//    VerificationToken verificationToken = new VerificationToken();
//    verificationToken.setToken(otp);
//    verificationToken.setExpiryDate(LocalDateTime.now().plusMinutes(5));
//    verificationToken.setUser(user);
//
//    // Lưu mã mới
//    tokenRepository.save(verificationToken);
//
//    // Tiến hành gửi Email
//    SimpleMailMessage message = new SimpleMailMessage();
//    message.setTo(email);
//    message.setSubject("[E-Voting System] Mã OTP Đặt Lại Mật Khẩu");
//    message.setText("Mã OTP của bạn là: " + otp + "\nMã này có hiệu lực trong vòng 5 phút.");
//
//    mailSender.send(message);
//    System.out.println(">>> [BE] Đã dọn dẹp token cũ, lưu mã OTP mới và gửi mail thành công!");
//  }
// 1. Hàm chính: Điều phối chung (KHÔNG dùng @Transactional ở đây)
public void sendOtpForgotPassword(String email) {
  Voter voter = voterRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống!"));

  User user = voter.getUser();

  // Sinh mã OTP 6 số
  String otp = String.format("%06d", new java.util.Random().nextInt(999999));

  // Gọi hàm phụ để lưu Token xuống DB và COMMIT ngay lập tức
  this.saveNewOtpToken(user, otp);

  // Tiến hành gửi Email (Hành động này nằm ngoài Transaction, nếu lỗi mạng cũng không bị mất dữ liệu DB)
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

  // 2. Hàm phụ: Chỉ lo nhiệm vụ DB độc lập và tự động COMMIT khi kết thúc
  @Transactional
  public void saveNewOtpToken(User user, String otp) {
    // Xóa sạch toàn bộ token cũ của User này
    tokenRepository.deleteByUser(user);
    tokenRepository.flush();

    // Tạo đối tượng token mới
    VerificationToken verificationToken = new VerificationToken();
    verificationToken.setToken(otp);
    verificationToken.setExpiryDate(LocalDateTime.now().plusMinutes(5));
    verificationToken.setUser(user);

    // Lưu và ép ghi hẳn xuống đĩa cứng
    tokenRepository.saveAndFlush(verificationToken);
    System.out.println(">>> [BE] Đã COMMIT vĩnh viễn OTP vào bảng verification_token cho User ID: " + user.getId());
  }

  @Transactional
  public void resetPasswordWithOtp(ResetPasswordWithOtpRequest request) {
    // Tìm mã OTP xem có tồn tại trong bảng verification_token khan
    VerificationToken verificationToken = (VerificationToken) tokenRepository.findByToken(request.getOtpCode())
        .orElseThrow(() -> new RuntimeException("Mã OTP không chính xác!"));

    // Kiểm tra xem mã OTP đó có đúng là thuộc về email đang yêu cầu đổi hay không
    if (!verificationToken.getUser().getEmail().equals(request.getEmail())) {
      throw new RuntimeException("Mã OTP không khớp với tài khoản yêu cầu!");
    }

    // Kiểm tra thời hạn hiệu lực của OTP
    if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
      tokenRepository.delete(verificationToken); // Xóa token hết hạn
      throw new RuntimeException("Mã OTP đã hết hiệu lực!");
    }

    // Nếu mọi thứ hợp lệ, tiến hành đổi mật khẩu cho User
    User user = verificationToken.getUser();
    user.setPassword(passwordEncoder.encode(request.getNewPassword()));

    // Đồng bộ thay đổi mật khẩu thông qua Voter hoặc UserRepository
    voterRepository.save(voterRepository.findByEmail(request.getEmail()).get());

    // Xóa mã OTP này đi sau khi đã sử dụng thành công
    tokenRepository.delete(verificationToken);
    System.out.println(">>> [BE] Đổi mật khẩu thành công, đã dọn dẹp bảng verification_token.");
  }
}
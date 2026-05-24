package com.nlu.voterservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.nlu.voterservice.dto.VoterResponse;
import com.nlu.voterservice.entity.User;
import com.nlu.voterservice.entity.Voter;
import com.nlu.voterservice.repository.UpdateProfileRequest;
import com.nlu.voterservice.repository.VoterRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoterService {

  @Autowired
  private VoterRepository voterRepository;
  @Autowired
  private Cloudinary cloudinary;
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
}
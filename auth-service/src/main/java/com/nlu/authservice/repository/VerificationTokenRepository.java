package com.nlu.authservice.repository;

import com.nlu.authservice.entity.User;
import com.nlu.authservice.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

  Optional<VerificationToken> findByToken(String token);

  // Tìm kiếm token theo user (dùng khi muốn xóa token cũ để gửi lại mã mới)
  Optional<VerificationToken> findByUser(User user);
}
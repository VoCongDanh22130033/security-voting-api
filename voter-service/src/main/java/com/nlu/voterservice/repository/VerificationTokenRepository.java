package com.nlu.voterservice.repository;

import com.nlu.voterservice.entity.User;
import com.nlu.voterservice.entity.VerificationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

  Optional<VerificationToken> findByToken(String token);

  void delete(VerificationToken verificationToken);
  void deleteByUser(User user);
}

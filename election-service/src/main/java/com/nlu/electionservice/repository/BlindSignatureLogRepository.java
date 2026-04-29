package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.BlindSignatureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BlindSignatureLogRepository extends JpaRepository<BlindSignatureLog, Long> {
  // Kiểm tra xem user đã xin chữ ký cho cuộc bầu cử này chưa
  Optional<BlindSignatureLog> findByUserIdAndElectionId(Long userId, Long electionId);
}
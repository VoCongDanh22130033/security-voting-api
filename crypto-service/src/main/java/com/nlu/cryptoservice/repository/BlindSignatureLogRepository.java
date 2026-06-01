package com.nlu.cryptoservice.repository;

import com.nlu.cryptoservice.entity.BlindSignatureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlindSignatureLogRepository extends JpaRepository<BlindSignatureLog, Long> {

  // CẬP NHẬT TẠI ĐÂY: Thêm tham số electionId để kiểm tra chính xác theo từng cuộc bầu cử cụ thể
  boolean existsByUserIdAndElectionIdAndRoundId(Long userId, Long electionId, Long roundId);
}
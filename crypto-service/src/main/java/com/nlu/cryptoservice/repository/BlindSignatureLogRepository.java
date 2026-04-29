package com.nlu.cryptoservice.repository;

import com.nlu.cryptoservice.entity.BlindSignatureLog; // Đúng Entity nội bộ
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlindSignatureLogRepository extends JpaRepository<BlindSignatureLog, Long> {
}
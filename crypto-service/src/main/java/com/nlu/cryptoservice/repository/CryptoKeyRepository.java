package com.nlu.cryptoservice.repository;

import com.nlu.cryptoservice.entity.CryptoKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CryptoKeyRepository extends JpaRepository<CryptoKey, Long> {
  Optional<CryptoKey> findFirstByElectionIdIsNull();
  Optional<CryptoKey> findByElectionId(Long electionId);
}
package com.nlu.cryptoservice.repository;

import com.nlu.cryptoservice.entity.CryptoKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CryptoKeyRepository extends JpaRepository<CryptoKey, Long> {
}
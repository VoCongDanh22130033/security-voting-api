package com.nlu.cryptoservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "crypto_keys")
@Data
public class CryptoKey {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "election_id")
  private Long electionId;

  @Column(name = "public_key", columnDefinition = "TEXT")
  private String publicKey;

  @Column(name = "private_key", columnDefinition = "TEXT")
  private String privateKey;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
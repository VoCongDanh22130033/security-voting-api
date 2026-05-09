package com.nlu.cryptoservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "crypto_keys")
@Data
public class CryptoKey {
  @Id
  @Column(name = "election_id")
  private Long electionId;

  @Column(name = "public_key", columnDefinition = "TEXT")
  private String publicKey;

  @Column(name = "private_key", columnDefinition = "TEXT")
  private String privateKey;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
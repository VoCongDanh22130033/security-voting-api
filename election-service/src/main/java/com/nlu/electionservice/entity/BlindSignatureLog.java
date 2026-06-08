package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "blind_signature_logs")
@Data
public class BlindSignatureLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "election_id")
  private Long electionId;

  @Column(name = "round_id")
  private Long roundId;

  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }
}

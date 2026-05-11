package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "votes")
@Data
public class Vote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "election_id")
  private Long electionId;

  @Column(name = "encrypted_vote", columnDefinition = "TEXT")
  private String blindedContent;

  @Column(name = "signature", columnDefinition = "TEXT")
  private String signature;

  @Column(name = "is_valid")
  private Boolean isValid;

  // insertable = false để MariaDB tự dùng current_timestamp()
  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
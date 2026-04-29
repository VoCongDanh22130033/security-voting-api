package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "anonymous_votes")
@Data
public class AnonymousVote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "election_id")
  private Long electionId;

  // Ánh xạ chính xác với cột blinded_content trong database
  @Column(name = "blinded_content", columnDefinition = "TEXT")
  private String blindedContent;

  @Column(name = "signature", columnDefinition = "TEXT")
  private String signature;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "candidate_id")
  private Long candidateId;
}
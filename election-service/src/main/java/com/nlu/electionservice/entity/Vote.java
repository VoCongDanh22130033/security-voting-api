package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "votes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "election_id", nullable = false)
  private Long electionId;

  @Column(name = "round_id", nullable = false)
  private Long roundId;

  @Column(name = "candidate_id", nullable = false)
  private Long candidateId;

  @Column(name = "message_token", columnDefinition = "TEXT", nullable = false)
  private String messageToken;

  @Column(name = "signature", columnDefinition = "TEXT", nullable = false)
  private String signature;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
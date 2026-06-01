package com.nlu.electionservice.entity;


import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "round_candidates")
@Data
public class RoundCandidate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "round_id", nullable = false)
  private ElectionRound round;

  @Column(name = "candidate_id", nullable = false)
  private Long candidateId;
}
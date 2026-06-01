package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "election_rounds")
@Data
public class ElectionRound {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "max_advance_count", nullable = false)
  private Integer maxAdvanceCount = 1;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "election_id", nullable = false)
  private Election election;
  private String title;
  @Column(name = "round_number", nullable = false)
  private Integer roundNumber;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(nullable = false)
  private String status = "UPCOMING";
}
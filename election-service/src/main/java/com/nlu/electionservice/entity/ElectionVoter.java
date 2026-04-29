package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "election_voters")
@IdClass(ElectionVoterId.class)
@Data
public class ElectionVoter {
  @Id
  @Column(name = "election_id")
  private Long electionId;

  @Id
  @Column(name = "voter_id")
  private Long voterId;

  @Column(name = "voted_at")
  private LocalDateTime votedAt = LocalDateTime.now();
}
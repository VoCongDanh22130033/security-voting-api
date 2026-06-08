package com.nlu.electionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Entity
@Table(name = "election_voter_invites")
@Data
public class ElectionVoterInvite {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "election_id", nullable = false)
  private Long electionId;

  @Column(name = "voter_id", nullable = false)
  private Long voterId;

  @Column(name = "round_id")
  private Long roundId;

  @Column(name = "round_number", nullable = false)
  private Integer roundNumber = 1;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "full_name")
  private String fullName;

  @Column(name = "citizen_id", nullable = false)
  private String citizenId;

  @Column(name = "invite_token", nullable = false, unique = true)
  private String inviteToken;

  @Column(name = "verified_at")
  private LocalDateTime verifiedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}

package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  // NULL khi phiếu được mã hóa — chỉ có giá trị sau khi giải mã
  @Column(name = "candidate_id")
  private Long candidateId;

  @Column(name = "message_token", columnDefinition = "TEXT", nullable = false)
  private String messageToken;

  @Column(name = "signature", columnDefinition = "TEXT", nullable = false)
  private String signature;

  // RSA-OAEP mã hóa JSON {"candidateId":X,"nonce":"uuid"} — server không biết nội dung
  @Column(name = "encrypted_vote", columnDefinition = "LONGTEXT")
  private String encryptedVote;
}
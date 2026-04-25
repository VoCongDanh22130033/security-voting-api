package com.nlu.electionservice.dto;

import lombok.Data;

@Data
public class VoteRequest {
  private Long electionId;
  private Long candidateId; // Nhận từ: { electionId, candidateId } trong Candidates.tsx
  private String encryptedVote;
  private String signature;
}
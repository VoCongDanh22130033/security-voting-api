package com.nlu.electionservice.dto;
import lombok.Data;

@Data
public class VoteRequest {
  private Long electionId;
  private Long candidateId;
  private String blindedContent;
  private String signature;
}
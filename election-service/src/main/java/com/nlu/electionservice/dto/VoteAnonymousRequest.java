package com.nlu.electionservice.dto;

import lombok.Data;

@Data
public class VoteAnonymousRequest {
  private Long electionId;
  private Long roundId;
  private Long candidateId;
  private String messageToken;
  private String signature;

}
package com.nlu.cryptoservice.dto;

import lombok.Data;

@Data
public class BlindRequest {
  private String blindedMessage;
  private Long electionId;
  private Long voterId;
}

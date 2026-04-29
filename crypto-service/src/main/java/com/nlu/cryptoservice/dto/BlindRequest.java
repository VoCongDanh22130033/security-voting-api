package com.nlu.cryptoservice.dto;

import lombok.Data;

@Data
public class BlindRequest {
  private String blindedMessage; // Base64 hoặc Hex của phiếu đã mù
  private Long electionId;
  private Long voterId;
}

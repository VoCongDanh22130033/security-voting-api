package com.nlu.notificationservice.dto;

import lombok.Data;

@Data
public class RoundClosedEmailRequest {
  private String to;
  private String fullName;
  private String electionTitle;
  private String roundTitle;
  private String resultLink;
}

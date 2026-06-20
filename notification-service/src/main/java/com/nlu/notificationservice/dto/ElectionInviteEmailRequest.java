package com.nlu.notificationservice.dto;

import lombok.Data;

@Data
public class ElectionInviteEmailRequest {
  private String to;
  private String fullName;
  private String electionTitle;
  private String roundTitle;
  private String inviteLink;
}

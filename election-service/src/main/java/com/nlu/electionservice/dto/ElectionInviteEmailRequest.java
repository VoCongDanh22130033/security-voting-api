package com.nlu.electionservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ElectionInviteEmailRequest {
  private String to;
  private String fullName;
  private String electionTitle;
  private String roundTitle;
  private String inviteLink;
}

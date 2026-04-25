package com.nlu.voterservice.dto;



import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoterResponse {
  private String username;
  private String email;
  private String role;
}
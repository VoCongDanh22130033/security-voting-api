package com.nlu.voterservice.dto;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoterResponse {
  private String username;
  private String email;
  private String role;
}
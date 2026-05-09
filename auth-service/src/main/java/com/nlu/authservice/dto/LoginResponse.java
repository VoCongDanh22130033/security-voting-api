package com.nlu.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
  private String token;
  private String fullName;
  private String email;
  private Set<String> roles;
  private Long id;
  private String image_url;

}
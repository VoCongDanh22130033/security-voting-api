package com.nlu.authservice.dto;

import lombok.Data;

@Data
public class CreateModeratorRequest {
  private String fullName;
  private String password;
  private String email;
  private String phone;
}


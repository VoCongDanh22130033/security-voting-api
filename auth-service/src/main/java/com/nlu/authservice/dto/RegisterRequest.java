package com.nlu.authservice.dto;

import lombok.Data;

@Data
public class RegisterRequest {
  private String fullName;
  private String password;
  private String email;
  private String phone;
}
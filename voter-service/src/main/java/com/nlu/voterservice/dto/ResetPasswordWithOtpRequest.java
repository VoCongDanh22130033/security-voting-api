package com.nlu.voterservice.dto;

import lombok.Data;

@Data
public class ResetPasswordWithOtpRequest {
  private String email;
  private String otpCode;
  private String newPassword;
}
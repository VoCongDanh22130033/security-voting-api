package com.nlu.voterservice.repository;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateProfileRequest {
  private String fullName;
  private UserPhoneDto user;
  private MultipartFile avatar;
  private Integer phone;


  @Data
  public static class UserPhoneDto {
    private String phone;
  }
}
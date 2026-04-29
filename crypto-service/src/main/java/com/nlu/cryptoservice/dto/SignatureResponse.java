package com.nlu.cryptoservice.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SignatureResponse {
  private String signature; // Base64 chữ ký mù của Server
}
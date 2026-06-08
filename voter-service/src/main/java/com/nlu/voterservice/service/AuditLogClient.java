package com.nlu.voterservice.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AuditLogClient {

  private final RestTemplate restTemplate = new RestTemplate();
  private final String auditServiceUrl = "http://localhost:8085/api/audit/log";

  public void log(String userEmail, String action, String details) {
    try {
      Map<String, String> payload = Map.of(
          "serviceName", "voter-service",
          "userEmail", userEmail != null ? userEmail : "system",
          "action", action,
          "details", details
      );
      restTemplate.postForEntity(auditServiceUrl, payload, Void.class);
    } catch (Exception e) {
      System.err.println(">>> Audit log failed: " + e.getMessage());
    }
  }
}

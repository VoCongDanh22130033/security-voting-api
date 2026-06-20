package com.nlu.voterservice.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AuditLogClient {

  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${app.audit-service-url:http://localhost:8085/api/audit/log}")
  private String auditServiceUrl;

  @Value("${app.internal-service-token}")
  private String internalToken;

  public void log(String userEmail, String action, String details) {
    try {
      Map<String, String> payload = Map.of(
          "serviceName", "voter-service",
          "userEmail", userEmail != null ? userEmail : "system",
          "action", action,
          "details", details
      );
      HttpHeaders h = new HttpHeaders();
      h.setContentType(MediaType.APPLICATION_JSON);
      h.set("X-Internal-Token", internalToken);
      restTemplate.postForEntity(auditServiceUrl, new HttpEntity<>(payload, h), Void.class);
    } catch (Exception e) {
      System.err.println(">>> Audit log failed: " + e.getMessage());
    }
  }
}

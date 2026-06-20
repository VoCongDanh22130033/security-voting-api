package com.nlu.electionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaProducerService {

  private static final String AUDIT_TOPIC = "audit-events";

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public void sendAuditEvent(String userEmail, String action, String details) {
    try {
      Map<String, String> payload = Map.of(
          "serviceName", "election-service",
          "userEmail", userEmail != null ? userEmail : "unknown",
          "action", action,
          "details", details != null ? details : "");
      kafkaTemplate.send(AUDIT_TOPIC, objectMapper.writeValueAsString(payload));
      log.info(">>> Đã gửi Kafka audit: {}", action);
    } catch (Exception e) {
      log.error(">>> Lỗi khi gửi Kafka audit: {}", e.getMessage());
    }
  }
}

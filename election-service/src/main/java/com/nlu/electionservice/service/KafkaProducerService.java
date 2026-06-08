package com.nlu.electionservice.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class KafkaProducerService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String AUDIT_SERVICE_URL = "http://localhost:8085/api/audit/log";

    public void sendAuditEvent(String userEmail, String action, String details) {
        try {
            Map<String, String> payload = Map.of(
                "serviceName", "election-service",
                "userEmail", userEmail,
                "action", action,
                "details", details
            );
            // Gửi trực tiếp API thay vì dùng Kafka
            restTemplate.postForEntity(AUDIT_SERVICE_URL, payload, Void.class);
            System.out.println(">>> Đã ghi log hệ thống: " + action);
        } catch (Exception e) {
            System.err.println(">>> Lỗi khi ghi log hệ thống: " + e.getMessage());
        }
    }
}

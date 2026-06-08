package com.nlu.auditservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlu.auditservice.dto.AuditLogRequest;
import com.nlu.auditservice.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaConsumerService(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "audit-events", groupId = "audit-group")
    public void consume(String message) {
        try {
            AuditLogRequest request = objectMapper.readValue(message, AuditLogRequest.class);
            auditLogService.logAction(request);
            System.out.println("Consumed and logged audit event: " + request.getAction());
        } catch (Exception e) {
            System.err.println("Failed to consume audit event: " + e.getMessage());
        }
    }
}

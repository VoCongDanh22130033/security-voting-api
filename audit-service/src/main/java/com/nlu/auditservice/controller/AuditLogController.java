package com.nlu.auditservice.controller;

import com.nlu.auditservice.dto.AuditLogRequest;
import com.nlu.auditservice.entity.AuditLog;
import com.nlu.auditservice.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Autowired
    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // Endpoint này được gọi từ các service khác (nếu không dùng Kafka) hoặc được gọi nội bộ bởi Kafka Listener
    @PostMapping("/log")
    public ResponseEntity<?> createLog(@RequestBody AuditLogRequest request) {
        auditLogService.logAction(request);
        return ResponseEntity.ok().build();
    }

    // Endpoint này được gọi từ frontend bởi Admin
    @GetMapping("/logs")
    public ResponseEntity<List<AuditLog>> getLogs() {
        return ResponseEntity.ok(auditLogService.getAllLogs());
    }
}

package com.nlu.auditservice.controller;

import com.nlu.auditservice.dto.AuditLogRequest;
import com.nlu.auditservice.entity.AuditLog;
import com.nlu.auditservice.repository.AuditLogRepository;
import com.nlu.auditservice.service.AuditLogService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

  private final AuditLogService auditLogService;
  private final AuditLogRepository auditLogRepository;

  @Autowired
  public AuditLogController(AuditLogService auditLogService, AuditLogRepository auditLogRepository) {
    this.auditLogService = auditLogService;
    this.auditLogRepository = auditLogRepository;
  }

  @PostMapping("/log")
  public ResponseEntity<?> createLog(@RequestBody AuditLogRequest request) {
    auditLogService.logAction(request);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/logs")
  public ResponseEntity<List<AuditLog>> getLogs(
      @RequestParam(required = false) String userEmail,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String serviceName,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    if (userEmail == null && action == null && serviceName == null && keyword == null && from == null && to == null) {
      return ResponseEntity.ok(auditLogService.getAllLogs());
    }
    return ResponseEntity.ok(auditLogService.searchLogs(
        userEmail,
        action,
        serviceName,
        keyword,
        parseDateTime(from),
        parseDateTime(to)));
  }

  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> getAuditStats() {
    LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("todayLogins", auditLogRepository.countByActionAndTimestampAfter("USER_LOGIN_SUCCESS", startOfDay));
    stats.put("failedLogins", auditLogRepository.countByAction("USER_LOGIN_FAILED"));
    stats.put("todayOtpSent", auditLogRepository.countByActionAndTimestampAfter("PASSWORD_RESET_OTP_REQUESTED", startOfDay));
    return ResponseEntity.ok(stats);
  }

  private LocalDateTime parseDateTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return LocalDateTime.parse(value);
  }
}

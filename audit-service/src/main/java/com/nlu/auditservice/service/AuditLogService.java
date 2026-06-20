package com.nlu.auditservice.service;

import com.nlu.auditservice.dto.AuditLogRequest;
import com.nlu.auditservice.entity.AuditLog;
import com.nlu.auditservice.repository.AuditLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;

  @Autowired
  public AuditLogService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  public void logAction(AuditLogRequest request) {
    AuditLog log = new AuditLog();
    log.setServiceName(request.getServiceName());
    log.setUserEmail(request.getUserEmail());
    log.setAction(request.getAction());
    log.setDetails(request.getDetails());
    auditLogRepository.save(log);
  }

  public List<AuditLog> getAllLogs() {
    return auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
  }

  public List<AuditLog> searchLogs(
      String userEmail,
      String action,
      String serviceName,
      String keyword,
      LocalDateTime fromTime,
      LocalDateTime toTime) {
    return auditLogRepository.search(
        formatLikeParam(userEmail),
        formatLikeParam(action),
        formatLikeParam(serviceName),
        formatLikeParam(keyword),
        fromTime,
        toTime);
  }

  private String formatLikeParam(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }
    return "%" + value.trim() + "%";
  }
}
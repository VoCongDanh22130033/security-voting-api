package com.nlu.auditservice.service;

import com.nlu.auditservice.dto.AuditLogRequest;
import com.nlu.auditservice.entity.AuditLog;
import com.nlu.auditservice.repository.AuditLogRepository;
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
}

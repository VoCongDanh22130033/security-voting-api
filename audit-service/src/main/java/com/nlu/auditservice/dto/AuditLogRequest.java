package com.nlu.auditservice.dto;

import lombok.Data;

@Data
public class AuditLogRequest {
    private String serviceName;
    private String userEmail;
    private String action;
    private String details;
}

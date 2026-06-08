package com.nlu.auditservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    private String serviceName; // Tên microservice gửi log (e.g., "auth-service", "election-service")

    private String userEmail; // Email của người thực hiện hành động

    private String action; // Mô tả hành động (e.g., "USER_LOGIN_SUCCESS", "ELECTION_CREATED", "ACCOUNT_LOCKED")

    @Lob
    @Column(columnDefinition = "TEXT")
    private String details; // Chi tiết về hành động (e.g., "User logged in from IP: 127.0.0.1", "Election ID: 123")

    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }
}

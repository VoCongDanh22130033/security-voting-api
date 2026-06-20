package com.nlu.auditservice.repository;

import com.nlu.auditservice.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  @Query("""
      SELECT a FROM AuditLog a
      WHERE (:userEmail IS NULL OR a.userEmail LIKE :userEmail)
        AND (:action IS NULL OR a.action LIKE :action)
        AND (:serviceName IS NULL OR a.serviceName LIKE :serviceName)
        AND (:keyword IS NULL OR a.details LIKE :keyword)
        AND (cast(:fromTime as timestamp) IS NULL OR a.timestamp >= :fromTime)
        AND (cast(:toTime as timestamp) IS NULL OR a.timestamp <= :toTime)
      ORDER BY a.timestamp DESC
      """)
  List<AuditLog> search(
      @Param("userEmail") String userEmail,
      @Param("action") String action,
      @Param("serviceName") String serviceName,
      @Param("keyword") String keyword,
      @Param("fromTime") LocalDateTime fromTime,
      @Param("toTime") LocalDateTime toTime);

  long countByAction(String action);

  long countByActionAndTimestampAfter(String action, LocalDateTime after);
}

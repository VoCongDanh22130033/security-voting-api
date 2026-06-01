package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "votes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 1. THÊM CỘT: Quản lý phiếu bầu trực tiếp theo Vòng đấu con thay vì cuộc bầu cử tổng phẳng
  @Column(name = "round_id", nullable = false)
  private Long roundId;

  // 2. THÊM CỘT: ID của ứng cử viên mà cử tri lựa chọn bỏ phiếu
  @Column(name = "candidate_id", nullable = false)
  private Long candidateId;

  // 3. THÊM CỘT: Mã số phiếu gốc M (đã giải mù) gửi từ Frontend lên hòm phiếu
  @Column(name = "message_token", columnDefinition = "TEXT", nullable = false)
  private String messageToken;

  // 4. THÊM CỘT: Chữ ký số S (đã giải mù) đi kèm để chứng minh phiếu hợp lệ
  @Column(name = "signature", columnDefinition = "TEXT", nullable = false)
  private String signature;

  // Tự động sử dụng thời gian thực tế hòm hụp từ MariaDB CURRENT_TIMESTAMP
  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
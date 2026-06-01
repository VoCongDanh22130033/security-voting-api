package com.nlu.voterservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens")
@Data
public class VerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "token", nullable = false)
  private String token; // Chúng ta sẽ lưu mã OTP 6 số vào đây

  @Column(name = "expiry_date", nullable = false)
  private LocalDateTime expiryDate;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", referencedColumnName = "id")
  private User user;
}
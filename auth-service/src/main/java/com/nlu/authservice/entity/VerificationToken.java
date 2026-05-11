package com.nlu.authservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens")
@Data
@NoArgsConstructor
public class VerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String token;

  @OneToOne
  @JoinColumn(name = "user_id")
  private User user;

  private LocalDateTime expiryDate;

  public VerificationToken(User user, String token) {
    this.user = user;
    this.token = token;
    // Hết hạn sau 15 phút
    this.expiryDate = LocalDateTime.now().plusMinutes(15);
  }
}
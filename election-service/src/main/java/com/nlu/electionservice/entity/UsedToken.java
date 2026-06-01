package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "used_tokens")
@Data
public class UsedToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "message_token", unique = true, nullable = false, length = 500)
  private String messageToken; // Lưu token M để chống xài lại

  @Column(name = "round_id")
  private Long roundId;

  private LocalDateTime createdAt = LocalDateTime.now();
}
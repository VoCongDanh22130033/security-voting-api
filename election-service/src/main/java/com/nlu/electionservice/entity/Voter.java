package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "voters")
@Data // Lombok sẽ tạo hàm getId() tại đây
public class Voter {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "full_name")
  private String fullName;
}
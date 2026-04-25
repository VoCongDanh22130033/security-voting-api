package com.nlu.voterservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "voters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Voter {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumn(name = "user_id") // Khớp với CONSTRAINT `voters_ibfk_1` trong SQL
  private User user;

  @Column(name = "full_name")
  private String fullName;

  @Column(name = "citizen_id")
  private String citizenId;

  @Column(name = "is_verified")
  private boolean isVerified;
}
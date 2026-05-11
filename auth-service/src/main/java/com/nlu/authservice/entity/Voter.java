package com.nlu.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "voters")
@Data
public class Voter {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @OneToOne
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "citizen_id")
  private String citizenId;

  @Column(name = "full_name")
  private String fullName;

  @Column(name = "is_verified")
  private boolean isVerified;
}
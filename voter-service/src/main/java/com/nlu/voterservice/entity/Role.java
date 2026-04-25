package com.nlu.voterservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
@Entity
@Table(name = "roles")
public class Role {

  @Id // Sử dụng duy nhất 1 ID này
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
}
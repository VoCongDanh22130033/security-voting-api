package com.nlu.electionservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  // Khóa ngoại liên kết tới bảng elections
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "election_id")
  @JsonIgnore // Tránh vòng lặp vô tận khi chuyển sang JSON
  private Election election;
}
package com.nlu.electionservice.entity;



import jakarta.persistence.*;

import lombok.Data;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "elections")
@Data
public class Election {
  @Id
  private Long id;
  private String title;
  private String description;

  @Column(name = "start_time") // Ánh xạ đúng cột start_time
  private LocalDateTime startTime;

  @Column(name = "end_time")   // Ánh xạ đúng cột end_time
  private LocalDateTime endTime;

  private String status;

  public LocalDateTime getStartDate() { return this.startTime; }
  public LocalDateTime getEndDate() { return this.endTime; }
}
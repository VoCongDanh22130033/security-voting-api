package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "elections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Where(clause = "is_delete = 1")
public class Election {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "role_id")
  private Long roleId;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  private String status;

  @Column(name = "is_delete")
  private Integer isDelete = 1;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Candidate> candidates = new ArrayList<>();

  public void setCandidates(List<Candidate> candidates) {
    this.candidates = candidates;
    if (candidates != null) {
      for (Candidate c : candidates) {
        c.setElection(this);
      }
    }
  }

  public LocalDateTime getStartDate() { return this.startTime; }
  public LocalDateTime getEndDate() { return this.endTime; }
}
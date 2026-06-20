package com.nlu.electionservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Where;

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

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  private String status;

  @Column(name = "is_delete")
  private Integer isDelete = 1;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "total_rounds")
  private Integer totalRounds;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "winner_candidate_id")
  private Long winnerId;

  @Column(name = "role_id")
  private Long roleId;

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

  public Long getWinnerId() {
    return this.winnerId;
  }
}

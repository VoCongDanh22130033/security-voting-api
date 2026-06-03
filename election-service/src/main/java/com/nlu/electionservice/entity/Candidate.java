package com.nlu.electionservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  @Column(name = "party")
  private String party;
  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "image_url", columnDefinition = "LONGTEXT")
  private String imageUrl;

  @Column(name = "vote_count")
  private int voteCount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "election_id", nullable = false)
  @JsonIgnore
  private Election election;

}
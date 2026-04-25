package com.nlu.electionservice.entity;



import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "elections")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Election {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "start_date")
  private LocalDateTime startDate;

  @Column(name = "end_date")
  private LocalDateTime endDate;

  @Column(length = 50)
  private String status; // Ví dụ: OPEN, CLOSED, UPCOMING

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  // Quan hệ 1 cuộc bầu cử có nhiều ứng viên
  @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<Candidate> candidates;
}
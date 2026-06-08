package com.nlu.electionservice.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "election_rounds")
@Data
public class ElectionRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private Election election;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "max_advance_count")
    private Integer maxAdvanceCount;
}

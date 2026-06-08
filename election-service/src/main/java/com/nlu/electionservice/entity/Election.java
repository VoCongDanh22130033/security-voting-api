package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // --- Cấu hình đối tượng bầu cử ---
    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false)
    private AudienceType audienceType = AudienceType.COMPANY_WIDE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "election_target_departments",
            joinColumns = @JoinColumn(name = "election_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private Set<Department> targetDepartments = new HashSet<>();
    // ------------------------------------

    // Các trường phục vụ hệ mã hóa đồng hình ElGamal cho tính năng E2EV
    @Column(name = "elgamal_p", columnDefinition = "TEXT")
    private String elGamalP;

    @Column(name = "elgamal_g", columnDefinition = "TEXT")
    private String elGamalG;

    @Column(name = "elgamal_h", columnDefinition = "TEXT")
    private String elGamalH; // Public key component

    @Column(name = "elgamal_x", columnDefinition = "TEXT")
    private String elGamalX;

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

    public enum AudienceType {
        COMPANY_WIDE, // Toàn công ty
        DEPARTMENT_SPECIFIC // Phòng ban cụ thể
    }
}

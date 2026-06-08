package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "encrypted_votes")
@Getter
@Setter
public class EncryptedVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long electionId;

    @Column(nullable = false, unique = true)
    private String receiptCode;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedChoice;

    @Column(nullable = false)
    private java.time.LocalDateTime castTime;
}

package com.nlu.electionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "public_bulletin_board")
@Getter
@Setter
public class PublicBulletinBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long electionId;

    @Column(nullable = false)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public PublicBulletinBoard() {
        this.timestamp = LocalDateTime.now();
    }
}

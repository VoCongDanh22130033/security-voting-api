package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.PublicBulletinBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicBulletinBoardRepository extends JpaRepository<PublicBulletinBoard, Long> {
    List<PublicBulletinBoard> findByElectionIdOrderByTimestampAsc(Long electionId);
}

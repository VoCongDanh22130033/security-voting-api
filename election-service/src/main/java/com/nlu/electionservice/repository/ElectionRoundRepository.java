package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.ElectionRound;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElectionRoundRepository extends JpaRepository<ElectionRound, Long> {

  List<ElectionRound> findByElectionId(Long electionId);

  Optional<ElectionRound> findByElectionIdAndRoundNumber(Long electionId, Integer roundNumber);

  @Query("SELECT er FROM ElectionRound er JOIN er.election e WHERE er.status = ?1 AND er.endTime < ?2 AND e.isDelete = 1")
  List<ElectionRound> findActiveRoundsByStatusAndEndTimeBefore(String status, LocalDateTime endTime);

  @Query("SELECT er FROM ElectionRound er JOIN er.election e WHERE er.status = ?1 AND er.startTime < ?2 AND e.isDelete = 1")
  List<ElectionRound> findUpcomingRoundsByStatusAndStartTimeBefore(String status, LocalDateTime startTime);

  List<ElectionRound> findByStatusAndEndTimeBefore(String status, LocalDateTime endTime);

  List<ElectionRound> findByStatusAndStartTimeBefore(String status, LocalDateTime startTime);

  Optional<ElectionRound> findByElectionIdAndStatus(Long electionId, String status);
}
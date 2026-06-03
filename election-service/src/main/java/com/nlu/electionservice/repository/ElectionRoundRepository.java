package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.ElectionRound;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElectionRoundRepository extends JpaRepository<ElectionRound, Long> {

  List<ElectionRound> findByElectionId(Long electionId);

  Optional<ElectionRound> findByElectionIdAndRoundNumber(Long electionId, Integer roundNumber);

  List<ElectionRound> findByStatusAndEndTimeBefore(String status, LocalDateTime endTime);

  List<ElectionRound> findByStatusAndStartTimeBefore(String status, LocalDateTime startTime);

  Optional<ElectionRound> findByElectionIdAndStatus(Long electionId, String status);
}
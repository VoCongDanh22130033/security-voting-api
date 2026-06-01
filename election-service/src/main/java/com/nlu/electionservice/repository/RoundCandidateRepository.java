package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.RoundCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoundCandidateRepository extends JpaRepository<RoundCandidate, Long> {

  List<RoundCandidate> findByRoundId(Long roundId);
}
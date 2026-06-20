package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.RoundCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoundCandidateRepository extends JpaRepository<RoundCandidate, Long> {

  List<RoundCandidate> findByRoundId(Long roundId);
  long countByRoundId(Long roundId);

  @Modifying
  @Query(value = "DELETE FROM round_candidates WHERE round_id = :roundId", nativeQuery = true)
  int deleteByRoundId(@Param("roundId") Long roundId);

  @Modifying
  @Query(value = "INSERT INTO round_candidates (round_id, candidate_id) " +
      "SELECT :nextRoundId, ranked.candidate_id " +
      "FROM ( " +
      "  SELECT rc.candidate_id, COUNT(v.id) AS vote_count " +
      "  FROM round_candidates rc " +
      "  LEFT JOIN votes v ON v.round_id = :currentRoundId " +
      "    AND v.candidate_id = rc.candidate_id " +
      "    AND v.election_id = :electionId " +
      "  WHERE rc.round_id = :currentRoundId " +
      "  GROUP BY rc.candidate_id " +
      "  ORDER BY vote_count DESC, rc.candidate_id ASC " +
      "  LIMIT :advanceCount " +
      ") ranked", nativeQuery = true)
  int insertAdvancingCandidates(
      @Param("electionId") Long electionId,
      @Param("currentRoundId") Long currentRoundId,
      @Param("nextRoundId") Long nextRoundId,
      @Param("advanceCount") Integer advanceCount);

  @Query(value = "SELECT COUNT(*) FROM round_candidates rc " +
      "JOIN candidates c ON c.id = rc.candidate_id " +
      "WHERE rc.round_id = :roundId AND rc.candidate_id = :candidateId " +
      "AND c.election_id = :electionId", nativeQuery = true)
  int countCandidateInElectionRound(
      @Param("electionId") Long electionId,
      @Param("roundId") Long roundId,
      @Param("candidateId") Long candidateId);
}

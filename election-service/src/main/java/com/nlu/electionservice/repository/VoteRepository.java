package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

  List<Vote> findByElectionIdAndEncryptedVoteIsNotNull(Long electionId);

  List<Vote> findByElectionIdAndRoundIdAndEncryptedVoteIsNotNull(Long electionId, Long roundId);

  List<Vote> findByElectionId(Long electionId);
  long countByElectionId(Long electionId);
  long countByElectionIdAndRoundId(Long electionId, Long roundId);
  long countByElectionIdAndCandidateIdIsNotNull(Long electionId);

  @Query(value = "SELECT v.candidate_id as candidateId, COUNT(v.id) as voteCount " +
          "FROM votes v " +
          "WHERE v.election_id = :electionId AND v.round_id = :roundId AND v.candidate_id IS NOT NULL " +
          "GROUP BY v.candidate_id " +
          "ORDER BY voteCount DESC", nativeQuery = true)
  List<Map<String, Object>> countVotesByCandidate(@Param("electionId") Long electionId, @Param("roundId") Long roundId);
}

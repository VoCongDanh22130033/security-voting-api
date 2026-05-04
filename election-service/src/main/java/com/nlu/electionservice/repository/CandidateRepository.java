package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

  List<Candidate> findByElectionId(Long electionId);

  void deleteByElectionId(Long electionId);

  @Query("SELECT COUNT(v) FROM AnonymousVote v WHERE v.candidateId = :candidateId AND v.electionId = :electionId")
  long countVotesByCandidateIdAndElectionId(@Param("candidateId") Long candidateId, @Param("electionId") Long electionId);

  @Query(value = "SELECT c.id, c.name, c.description, c.image_url, COUNT(v.id) AS vote_count " +
      "FROM candidates c " +
      "LEFT JOIN anonymous_votes v ON c.id = v.candidate_id " +
      "WHERE c.election_id = :electionId " +
      "GROUP BY c.id, c.name, c.description, c.image_url",
      nativeQuery = true)
  List<Object[]> findCandidatesWithVoteCountNative(@Param("electionId") Long electionId);
}
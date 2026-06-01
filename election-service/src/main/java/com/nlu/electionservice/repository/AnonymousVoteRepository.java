package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.AnonymousVote;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AnonymousVoteRepository extends JpaRepository<AnonymousVote, Long> {

  @Query(value = "SELECT candidate_id AS candidate_id, COUNT(*) AS vote_count " +
      "FROM votes " +
      "GROUP BY candidate_id", nativeQuery = true)
  List<Map<String, Object>> countVotesByCandidate();
}
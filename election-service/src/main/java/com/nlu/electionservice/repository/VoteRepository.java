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

  // Truy vấn gom nhóm đếm tổng số phiếu thực tế của từng ứng viên theo Election và Round
  @Query(value = "SELECT candidate_id as candidateId, COUNT(*) as voteCount " +
      "FROM votes " +
      "WHERE election_id = :electionId AND round_id = :roundId " +
      "GROUP BY candidate_id", nativeQuery = true)
  List<Map<String, Object>> countVotesByCandidate(@Param("electionId") Long electionId, @Param("roundId") Long roundId);
}
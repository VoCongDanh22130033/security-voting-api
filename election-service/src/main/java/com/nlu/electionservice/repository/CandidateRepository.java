package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
      "INNER JOIN round_candidates rc ON c.id = rc.candidate_id " +
      "INNER JOIN election_rounds er ON rc.round_id = er.id " +
      "LEFT JOIN anonymous_votes v ON c.id = v.candidate_id " +
      "WHERE c.election_id = :electionId AND er.status = 'OPEN' " +
      "GROUP BY c.id, c.name, c.description, c.image_url",
      nativeQuery = true)
  List<Object[]> findCandidatesWithVoteCountNative(@Param("electionId") Long electionId);

  // Lấy ứng viên theo vòng bầu cử cụ thể, đã được sắp xếp theo số phiếu giảm dần
  @Query(value = "SELECT DISTINCT c.id, c.name, c.description, c.image_url, COUNT(v.id) AS vote_count " +
      "FROM candidates c " +
      "INNER JOIN round_candidates rc ON c.id = rc.candidate_id " +
      "LEFT JOIN votes v ON c.id = v.candidate_id AND v.round_id = :roundId " +
      "WHERE rc.round_id = :roundId " +
      "GROUP BY c.id, c.name, c.description, c.image_url " +
      "ORDER BY vote_count DESC", // SẮP XẾP THEO SỐ PHIẾU GIẢM DẦN
      nativeQuery = true)
  List<Object[]> findCandidatesByRoundWithVotes(@Param("roundId") Long roundId);

  @Query("SELECT c FROM Candidate c JOIN RoundCandidate rc ON c.id = rc.candidateId WHERE rc.round.id = :roundId")
  List<Candidate> findAllByRoundId(@Param("roundId") Long roundId);

  @Modifying
  @Query(value = "UPDATE candidates SET vote_count = vote_count + 1 WHERE id = :candidateId", nativeQuery = true)
  void incrementVoteCount(@Param("candidateId") Long candidateId);
}
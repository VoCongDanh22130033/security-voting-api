package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.BlindSignatureLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BlindSignatureLogRepository extends JpaRepository<BlindSignatureLog, Long> {
  Optional<BlindSignatureLog> findByUserIdAndElectionId(Long userId, Long electionId);

  List<BlindSignatureLog> findByUserId(Long userId);

  boolean existsByUserIdAndElectionIdAndRoundId(Long userId, Long electionId, Long roundId);

  List<BlindSignatureLog> findByElectionIdOrderByRoundIdAscCreatedAtAsc(Long electionId);

  @Query(value = "SELECT COUNT(DISTINCT user_id) FROM blind_signature_logs WHERE election_id = :electionId", nativeQuery = true)
  long countDistinctVotedByElectionId(@Param("electionId") Long electionId);

  @Query(value = "SELECT COUNT(DISTINCT user_id) FROM blind_signature_logs WHERE election_id = :electionId AND round_id = :roundId", nativeQuery = true)
  long countDistinctVotedByElectionIdAndRoundId(@Param("electionId") Long electionId, @Param("roundId") Long roundId);

  @Query(value = "SELECT MIN(created_at) FROM blind_signature_logs WHERE election_id = :electionId", nativeQuery = true)
  java.time.LocalDateTime findFirstVoteTime(@Param("electionId") Long electionId);

  @Query(value = "SELECT MAX(created_at) FROM blind_signature_logs WHERE election_id = :electionId", nativeQuery = true)
  java.time.LocalDateTime findLastVoteTime(@Param("electionId") Long electionId);
}

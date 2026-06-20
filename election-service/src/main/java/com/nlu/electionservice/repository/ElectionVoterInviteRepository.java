package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.ElectionVoterInvite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ElectionVoterInviteRepository extends JpaRepository<ElectionVoterInvite, Long> {
  Optional<ElectionVoterInvite> findByInviteToken(String inviteToken);

  Optional<ElectionVoterInvite> findByElectionIdAndVoterIdAndRoundNumber(Long electionId, Long voterId, Integer roundNumber);

  boolean existsByElectionIdAndEmailAndRoundNumber(Long electionId, String email, Integer roundNumber);

  List<ElectionVoterInvite> findByElectionIdAndRoundNumber(Long electionId, Integer roundNumber);

  List<ElectionVoterInvite> findByElectionIdOrderByCreatedAtDesc(Long electionId);

  @Query(value = "SELECT DISTINCT election_id FROM election_voter_invites WHERE citizen_id = :citizenId", nativeQuery = true)
  List<Long> findElectionIdsByCitizenId(@Param("citizenId") String citizenId);

  @Query(value = "SELECT COUNT(DISTINCT voter_id) FROM election_voter_invites WHERE election_id = :electionId", nativeQuery = true)
  long countInvitedByElectionId(@Param("electionId") Long electionId);

  @Query(value = "SELECT COUNT(DISTINCT voter_id) FROM election_voter_invites WHERE election_id = :electionId AND verified_at IS NOT NULL", nativeQuery = true)
  long countVerifiedByElectionId(@Param("electionId") Long electionId);

  @Query(value = "SELECT COUNT(*) FROM election_voter_invites WHERE election_id = :electionId AND round_number = :roundNumber", nativeQuery = true)
  long countInvitedByElectionIdAndRoundNumber(@Param("electionId") Long electionId, @Param("roundNumber") Integer roundNumber);

  @Query(value = "SELECT COUNT(*) FROM election_voter_invites WHERE election_id = :electionId AND round_number = :roundNumber AND verified_at IS NOT NULL", nativeQuery = true)
  long countVerifiedByElectionIdAndRoundNumber(@Param("electionId") Long electionId, @Param("roundNumber") Integer roundNumber);

  long countByElectionId(Long electionId);
  long countByElectionIdAndVerifiedAtIsNotNull(Long electionId);
}

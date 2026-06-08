package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.ElectionVoterInvite;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ElectionVoterInviteRepository extends JpaRepository<ElectionVoterInvite, Long> {
  Optional<ElectionVoterInvite> findByInviteToken(String inviteToken);

  Optional<ElectionVoterInvite> findByElectionIdAndVoterIdAndRoundNumber(Long electionId, Long voterId, Integer roundNumber);

  boolean existsByElectionIdAndEmailAndRoundNumber(Long electionId, String email, Integer roundNumber);
}

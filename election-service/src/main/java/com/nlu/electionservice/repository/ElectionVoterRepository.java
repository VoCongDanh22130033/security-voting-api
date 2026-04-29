package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.ElectionVoter;
import com.nlu.electionservice.entity.ElectionVoterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
// Kế thừa ElectionVoterId nếu bạn đã tạo class ID, nếu chưa hãy dùng ElectionVoter, Long
public interface ElectionVoterRepository extends JpaRepository<ElectionVoter, ElectionVoterId> {

  @Query(value = "SELECT COUNT(*) FROM election_voters WHERE election_id = :eId AND voter_id = :vId", nativeQuery = true)
  int countByElectionIdAndVoterId(@Param("eId") Long electionId, @Param("vId") Long voterId);

  @Modifying
  @Transactional
  @Query(value = "INSERT INTO election_voters (election_id, voter_id, voted_at) VALUES (:eId, :vId, NOW())", nativeQuery = true)
  void insertElectionVoter(@Param("eId") Long electionId, @Param("vId") Long voterId);
}
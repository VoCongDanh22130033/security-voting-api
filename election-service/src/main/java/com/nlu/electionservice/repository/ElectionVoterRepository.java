package com.nlu.electionservice.repository;



import com.nlu.electionservice.entity.Vote;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nlu.electionservice.entity.Vote; // Dùng tạm JpaRepository của một Entity bất kỳ
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ElectionVoterRepository extends JpaRepository<Vote, Long> {

  @Query(value = "SELECT COUNT(*) FROM election_voters WHERE election_id = :eId AND voter_id = :vId", nativeQuery = true)
  int countByElectionIdAndVoterId(@Param("eId") Long electionId, @Param("vId") Long voterId);

  @Modifying
  @Transactional // Quan trọng để thực hiện lệnh ghi xuống DB
  @Query(value = "INSERT INTO election_voters (election_id, voter_id, voted_at) VALUES (:eId, :vId, NOW())", nativeQuery = true)
  void insertElectionVoter(@Param("eId") Long electionId, @Param("vId") Long voterId);
}
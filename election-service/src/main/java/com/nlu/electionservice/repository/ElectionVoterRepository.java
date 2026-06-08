package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.ElectionVoter;
import com.nlu.electionservice.entity.ElectionVoterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
// Kế thừa ElectionVoterId nếu bạn đã tạo class ID, nếu chưa hãy dùng ElectionVoter, Long
public interface ElectionVoterRepository extends JpaRepository<ElectionVoter, ElectionVoterId> {

  @Query(value = "SELECT COUNT(*) FROM election_voters WHERE election_id = :eId AND voter_id = :vId", nativeQuery = true)
  int countByElectionIdAndVoterId(@Param("eId") Long electionId, @Param("vId") Long voterId);

  @Query(value = "SELECT voter_id FROM election_voters WHERE election_id = :eId", nativeQuery = true)
  List<Long> findVoterIdsByElectionId(@Param("eId") Long electionId);

  @Modifying
  @Transactional
  @Query(value = "INSERT IGNORE INTO election_voters (election_id, voter_id, voted_at) VALUES (:eId, :vId, NULL)", nativeQuery = true)
  void insertElectionVoter(@Param("eId") Long electionId, @Param("vId") Long voterId);

  @Modifying
  @Transactional
  @Query(value = "DELETE FROM election_voters WHERE election_id = :eId", nativeQuery = true)
  void deleteByElectionId(@Param("eId") Long electionId);

  @Modifying
  @Transactional
  @Query(value = """
      INSERT IGNORE INTO election_voters (election_id, voter_id, voted_at)
      SELECT :eId, v.user_id, NULL
      FROM voters v
      JOIN users u ON u.id = v.user_id
      JOIN employees emp ON emp.id = u.employee_id
      WHERE emp.is_active = 1
      """, nativeQuery = true)
  int insertCompanyWideEligibleVoters(@Param("eId") Long electionId);

  @Modifying
  @Transactional
  @Query(value = """
      INSERT IGNORE INTO election_voters (election_id, voter_id, voted_at)
      SELECT :eId, v.user_id, NULL
      FROM voters v
      JOIN users u ON u.id = v.user_id
      JOIN employees emp ON emp.id = u.employee_id
      WHERE emp.is_active = 1 AND emp.department_id IN (:departmentIds)
      """, nativeQuery = true)
  int insertDepartmentEligibleVoters(@Param("eId") Long electionId, @Param("departmentIds") List<Long> departmentIds);
}

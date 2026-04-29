package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.Voter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VoterRepository extends JpaRepository<Voter, Long> {
  // Truy vấn để lấy voter_id dựa trên email của User
  @Query(value = "SELECT v.id FROM voters v JOIN users u ON v.user_id = u.id WHERE u.email = :email", nativeQuery = true)
  Optional<Long> findVoterIdByEmail(@Param("email") String email);
}
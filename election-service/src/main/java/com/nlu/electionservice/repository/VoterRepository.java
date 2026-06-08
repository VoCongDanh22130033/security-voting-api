package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.Voter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VoterRepository extends JpaRepository<Voter, Long> {
  @Query(value = "SELECT v.user_id FROM voters v JOIN users u ON v.user_id = u.id WHERE u.email = :email", nativeQuery = true)
  Optional<Long> findVoterIdByEmail(@Param("email") String email);

  @Query(value = "SELECT v.* FROM voters v JOIN users u ON v.user_id = u.id WHERE u.email = :email LIMIT 1", nativeQuery = true)
  Optional<Voter> findByEmail(@Param("email") String email);

  Optional<Voter> findByCitizenId(String citizenId);
}

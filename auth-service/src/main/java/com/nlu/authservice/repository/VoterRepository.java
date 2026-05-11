package com.nlu.authservice.repository;

import com.nlu.authservice.entity.Voter;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoterRepository extends JpaRepository<Voter, Long> {
  @Query("SELECT v FROM Voter v JOIN v.user u WHERE u.email = :email")
  Optional<Voter> findByEmail(@Param("email") String email);
}
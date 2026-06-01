package com.nlu.voterservice.repository;

import com.nlu.voterservice.entity.Voter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface VoterRepository extends JpaRepository<Voter, Long> {
  @Query("SELECT v FROM Voter v JOIN v.user u WHERE u.email = :email")
  Optional<Voter> findByEmail(@Param("email") String email);

  @Query("SELECT v FROM Voter v JOIN v.user u JOIN u.roles r WHERE r.id = :roleId")
  java.util.List<Voter> findByRoleId(@Param("roleId") Long roleId);
}
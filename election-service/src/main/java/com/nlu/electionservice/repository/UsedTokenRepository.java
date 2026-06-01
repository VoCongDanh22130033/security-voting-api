package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.UsedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsedTokenRepository extends JpaRepository<UsedToken, Long> {

  boolean existsByMessageToken(String messageToken);
}
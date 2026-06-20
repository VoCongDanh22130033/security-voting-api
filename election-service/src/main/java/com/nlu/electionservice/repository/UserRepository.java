package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.User;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  List<User> findAllByOrderByIdDesc();
  long countByRoles_Name(String roleName);
  long countByIsLock(Integer isLock);
  Optional<User> findByEmail(String email);
  boolean existsByEmail(String email);
}

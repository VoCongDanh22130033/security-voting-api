package com.nlu.authservice.repository;


import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);
}
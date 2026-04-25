package com.nlu.authservice.repository;

import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
  Optional<Role> findByName(String name);
}
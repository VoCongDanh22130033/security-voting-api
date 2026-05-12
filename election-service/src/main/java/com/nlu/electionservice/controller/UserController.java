package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.UserResponse;
import com.nlu.electionservice.entity.User;
import com.nlu.electionservice.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/elections/voter")
public class UserController {
  @Autowired
  private UserRepository userRepository;

  @GetMapping("/all")
  public ResponseEntity<List<UserResponse>> getAllUsers() {
    List<User> users = userRepository.findAllByOrderByIdDesc();
    List<UserResponse> response = users.stream().map(u -> {
      UserResponse dto = new UserResponse();
      dto.setId(u.getId());
      dto.setFullName(u.getFullName());
      dto.setEmail(u.getEmail());
      dto.setPhone(u.getPhone());
      if (u.getRoles() != null && !u.getRoles().isEmpty()) {
        String name = u.getRoles().iterator().next().getName();
        dto.setRoleName(name.startsWith("ROLE_") ? name.substring(5) : name);
      } else {
        dto.setRoleName("USER");
      }

      return dto;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }
}
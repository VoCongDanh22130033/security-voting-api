package com.nlu.voterservice.service;


import com.nlu.voterservice.dto.VoterResponse;
import com.nlu.voterservice.entity.Voter;
import com.nlu.voterservice.repository.VoterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VoterService {

  @Autowired
  private VoterRepository voterRepository;

  public VoterResponse getProfile(String username) {
    Voter voter = voterRepository.findByUsername(username)
        .orElseThrow(() -> new RuntimeException("Voter không tồn tại"));

    // Lấy tên role đầu tiên (ví dụ: ROLE_VOTER) từ Set roles
    String roleName = voter.getUser().getRoles().stream()
        .findFirst()
        .map(role -> role.getName())
        .orElse("USER");

    return new VoterResponse(
        voter.getUser().getUsername(),
        voter.getUser().getEmail(),
        roleName
    );
  }
}
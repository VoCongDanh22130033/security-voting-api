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

  public VoterResponse getProfile(String email) {
    Voter voter = voterRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Cử tri không tồn tại với email: " + email));

    // Lấy tên role đầu tiên từ danh sách roles của User
    String roleName = voter.getUser().getRoles().stream()
        .findFirst()
        .map(role -> role.getName())
        .orElse("ROLE_VOTER");

    return new VoterResponse(
        voter.getUser().getEmail(),
        roleName
    );
  }

  public Voter findByEmail(String email) {
    return voterRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cử tri với email: " + email));
  }

}
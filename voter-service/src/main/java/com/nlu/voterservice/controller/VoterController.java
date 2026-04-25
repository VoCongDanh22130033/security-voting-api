package com.nlu.voterservice.controller;

import com.nlu.voterservice.dto.VoterResponse;
import com.nlu.voterservice.service.VoterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voter")
public class VoterController {

  @Autowired
  private VoterService voterService;

  @GetMapping("/profile")
  public ResponseEntity<VoterResponse> getProfile(@RequestHeader("X-User") String username) {
    // Gateway truyền username qua X-User, Service xử lý logic Map DTO
    VoterResponse response = voterService.getProfile(username);
    return ResponseEntity.ok(response);
  }
}
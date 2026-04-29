package com.nlu.voterservice.controller;

import com.nlu.voterservice.dto.VoterResponse;
import com.nlu.voterservice.entity.Voter;
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
  public ResponseEntity<?> getProfile(@RequestHeader("X-User-Email") String email) {
    try {
      Voter voter = voterService.findByEmail(email);
      return ResponseEntity.ok(voter);
    } catch (Exception e) {

      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
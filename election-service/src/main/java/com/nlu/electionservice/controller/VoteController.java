package com.nlu.electionservice.controller;


import com.nlu.electionservice.dto.VoteRequest;

import com.nlu.electionservice.service.VoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/votes")
public class VoteController {

  @Autowired
  private VoteService voteService;

  @PostMapping("/cast")
  public ResponseEntity<String> castVote(@RequestBody VoteRequest request,
      @RequestHeader("X-User") String username) {
    try {
      voteService.saveVote(request, username);
      return ResponseEntity.ok("Bỏ phiếu thành công!");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Lỗi khi bỏ phiếu: " + e.getMessage());
    }
  }
}
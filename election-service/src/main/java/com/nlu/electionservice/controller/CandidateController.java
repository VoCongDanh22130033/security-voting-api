package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.service.CandidateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/elections")
public class CandidateController {

  @Autowired
  private CandidateService candidateService;

  @GetMapping("/{electionId}/candidates")
  public ResponseEntity<List<CandidateResponse>> getCandidates(@PathVariable Long electionId) {
    List<CandidateResponse> response = candidateService.getCandidatesWithVotes(electionId);
    return ResponseEntity.ok(response);
  }
}
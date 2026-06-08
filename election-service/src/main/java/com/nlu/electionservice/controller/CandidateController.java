package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.entity.Candidate;
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

  @GetMapping("/rounds/{roundId}/candidates")
  public ResponseEntity<List<CandidateResponse>> getCandidatesByRound(@PathVariable Long roundId) {
    List<CandidateResponse> response = candidateService.getCandidatesByRound(roundId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/round/{roundId}")
  public ResponseEntity<?> getCandidatesByRoundId(@PathVariable Long roundId) {
    try {
      System.out.println(">>> [BE Election] Lấy danh sách ứng viên hợp lệ cho Vòng đấu ID: " + roundId);
      List<Candidate> candidates = candidateService.getCandidatesByRoundId(roundId);
      return ResponseEntity.ok(candidates);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Lỗi khi tải ứng viên theo vòng: " + e.getMessage());
    }
  }

  @GetMapping("/candidates/all")
  public ResponseEntity<List<Candidate>> getAllCandidates() {
    return ResponseEntity.ok(candidateService.getAllCandidates());
  }

}
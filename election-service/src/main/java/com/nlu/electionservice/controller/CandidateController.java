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

  // ĐÂY LÀ ENDPOINT DUY NHẤT XỬ LÝ LẤY ỨNG VIÊN
  @GetMapping("/{electionId}/candidates")
  public ResponseEntity<List<CandidateResponse>> getCandidates(@PathVariable Long electionId) {
    // Gọi hàm Service đã có logic LEFT JOIN để lấy voteCount = 4
    List<CandidateResponse> response = candidateService.getCandidatesWithVotes(electionId);
    return ResponseEntity.ok(response);
  }
}
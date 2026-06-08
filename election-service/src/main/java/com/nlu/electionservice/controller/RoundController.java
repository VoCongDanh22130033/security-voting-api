package com.nlu.electionservice.controller;

import com.nlu.electionservice.service.RoundService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rounds")
public class RoundController {

  @Autowired
  private RoundService roundService;

  @PostMapping("/{roundId}/start")
  public ResponseEntity<?> startRound(@PathVariable Long roundId) {
    try {
      return ResponseEntity.ok(roundService.startRound(roundId));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{roundId}/close")
  public ResponseEntity<?> closeRound(@PathVariable Long roundId) {
    try {
      return ResponseEntity.ok(roundService.closeRound(roundId));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/{roundId}/tally")
  public ResponseEntity<?> tally(@PathVariable Long roundId, @RequestParam Long electionId) {
    try {
      return ResponseEntity.ok(roundService.tallyRound(electionId, roundId));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(e.getMessage());
    }
  }

  @PostMapping("/{roundId}/advance")
  public ResponseEntity<?> advance(@PathVariable Long roundId) {
    try {
      Map<String, Object> result = roundService.advanceRound(roundId);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}


package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.ElectionResponse;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.service.ElectionService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {
  @Autowired
  private ElectionService electionService;

  @GetMapping
  public ResponseEntity<List<ElectionResponse>> getAll() {
    List<Election> elections = electionService.getAllElections();

    // Chuyển đổi Entity -> DTO
    List<ElectionResponse> response = elections.stream().map(e -> {
      ElectionResponse dto = new ElectionResponse();
      dto.setId(e.getId());
      dto.setTitle(e.getTitle());
      dto.setDescription(e.getDescription());
      dto.setStatus(e.getStatus());
      dto.setStartDate(e.getStartDate());
      dto.setEndDate(e.getEndDate());
      return dto;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}/candidates")
  public ResponseEntity<List<Candidate>> getCandidates(@PathVariable Long id) {
    return ResponseEntity.ok(electionService.getCandidatesByElection(id));
  }
}
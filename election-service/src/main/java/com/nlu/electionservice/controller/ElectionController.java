package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.ElectionRequest;
import com.nlu.electionservice.dto.ElectionResponse;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.service.ElectionService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {
  @Autowired
  private ElectionService electionService;
  @GetMapping
  public ResponseEntity<List<ElectionResponse>> getAll() {
    List<Election> elections = electionService.getAllElections();

    List<ElectionResponse> response = elections.stream().map(e -> {
      ElectionResponse dto = new ElectionResponse();
      dto.setId(e.getId());
      dto.setTitle(e.getTitle());
      dto.setDescription(e.getDescription());
      dto.setStatus(e.getStatus());
      dto.setStartDate(e.getStartDate());
      dto.setEndDate(e.getEndDate());

      // SỬA LỖI: Trả về roleId
      dto.setRoleId(e.getRoleId());
      return dto;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }

  @PostMapping("/create")
  public ResponseEntity<Election> create(@RequestBody ElectionRequest request) {
    // SỬA LỖI LOG: Log đúng tên trường roleId
    System.out.println("Role ID nhận được: " + request.getRoleId());
    return ResponseEntity.ok(electionService.createElection(request));
  }
  @GetMapping("/{id}/candidates")
  public ResponseEntity<List<Candidate>> getCandidates(@PathVariable Long id) {
    return ResponseEntity.ok(electionService.getCandidatesByElection(id));
  }


  @PutMapping("/{id}")
  public ResponseEntity<Election> update(@PathVariable Long id, @RequestBody ElectionRequest request) {
    return ResponseEntity.ok(electionService.updateElection(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    electionService.deleteElection(id);
    return ResponseEntity.noContent().build();
  }
  @GetMapping("/{id}")
  public ResponseEntity<ElectionResponse> getById(@PathVariable Long id) {
    // Giả sử bạn đã có hàm findById trong Service trả về Entity Election
    Election e = electionService.getById(id);

    ElectionResponse dto = new ElectionResponse();
    dto.setId(e.getId());
    dto.setTitle(e.getTitle());
    dto.setDescription(e.getDescription());
    dto.setStatus(e.getStatus());
    dto.setStartDate(e.getStartDate());
    dto.setEndDate(e.getEndDate());
    dto.setRoleId(e.getRoleId());

    return ResponseEntity.ok(dto);
  }


}
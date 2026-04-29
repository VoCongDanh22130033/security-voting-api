package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.ElectionRequest;
import com.nlu.electionservice.dto.ElectionResponse;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.service.ElectionService;
import com.nlu.electionservice.service.CloudinaryService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {

  @Autowired
  private CloudinaryService cloudinaryService;
  @Autowired
  private ElectionService electionService;
  @Autowired
  private ElectionRepository electionRepository;

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

      dto.setImage(e.getImage());
      dto.setRoleId(e.getRoleId());
      return dto;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }

  @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> createElection(
      @RequestPart("election") Election election,
      @RequestPart("file") MultipartFile file) {

    try {
      // 1. Upload ảnh lên Cloudinary lấy URL
      String imageUrl = cloudinaryService.uploadFile(file);

      // 2. Gán URL vào Entity qua hàm setImage đã đồng bộ
      election.setImage(imageUrl);

      // 3. Gọi Service để xử lý lưu Election và Candidate tập trung[cite: 10]
      // Lưu ý: Đảm bảo class Election của bạn có chứa List<Candidate> nếu muốn lưu đồng thời
      Election savedElection = electionService.createElection(election, null);

      return ResponseEntity.ok(savedElection);
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.internalServerError().body("Lỗi hệ thống: " + e.getMessage());
    }
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
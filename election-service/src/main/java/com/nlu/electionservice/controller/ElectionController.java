package com.nlu.electionservice.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.dto.ElectionRequest;
import com.nlu.electionservice.dto.ElectionResponse;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.service.ElectionService;
import com.nlu.electionservice.service.CloudinaryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());


  @PostMapping("/upload-image")
  public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadFile(file);
    return ResponseEntity.ok(java.util.Map.of("url", url));
  }

//  public ResponseEntity<List<ElectionResponse>> getAll() {
//    List<Election> elections = electionService.getAllElections();
//
//    List<ElectionResponse> response = elections.stream().map(e -> {
//      ElectionResponse dto = new ElectionResponse();
//      dto.setId(e.getId());
//      dto.setTitle(e.getTitle());
//      dto.setDescription(e.getDescription());
//      dto.setStatus(e.getStatus());
//      dto.setStartDate(e.getStartTime());
//      dto.setEndDate(e.getEndTime());
//      dto.setImage(e.getImage());
//      dto.setRoleId(e.getRoleId());
//
//      if(e.getCandidates() != null) {
//        dto.setCandidates(e.getCandidates().stream().map(c ->
//            CandidateResponse.builder()
//                .id(c.getId())
//                .name(c.getName())
//                .imageUrl(c.getImageUrl())
//                .description(c.getDescription())
//                .build()
//        ).collect(Collectors.toList()));
//      }
//      return dto;
//    }).collect(Collectors.toList());
//
//    return ResponseEntity.ok(response);
//  }
  @GetMapping
  public ResponseEntity<List<ElectionResponse>> getAll() {
    // 1. Lấy danh sách trực tiếp từ repository và sắp xếp ID giảm dần (mới nhất lên đầu)
    List<Election> list = electionRepository.findAll(org.springframework.data.domain.Sort.by(
        org.springframework.data.domain.Sort.Direction.DESC, "id"));

    // 2. Map từ danh sách 'list' (đã sắp xếp) sang 'ElectionResponse'
    List<ElectionResponse> response = list.stream().map(e -> {
      ElectionResponse dto = new ElectionResponse();
      dto.setId(e.getId());
      dto.setTitle(e.getTitle());
      dto.setDescription(e.getDescription());
      dto.setStatus(e.getStatus());
      dto.setStartDate(e.getStartTime());
      dto.setEndDate(e.getEndTime());
      dto.setImage(e.getImageUrl());
      dto.setRoleId(e.getRoleId());

      if (e.getCandidates() != null) {
        dto.setCandidates(e.getCandidates().stream().map(c ->
            CandidateResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .imageUrl(c.getImageUrl())
                .description(c.getDescription())
                .build()
        ).collect(Collectors.toList()));
      }
      return dto;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }
  @PostMapping("/create")
  public ResponseEntity<?> create(@RequestPart("election") String electionJson,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    try {
      Election election = mapper.readValue(electionJson, Election.class);

      if (file != null && !file.isEmpty()) {
        String imageUrl = cloudinaryService.uploadFile(file);
        election.setImageUrl(imageUrl);
      }

      if (election.getStatus() == null) election.setStatus("OPEN");

      if (election.getCandidates() != null) {
        election.getCandidates().forEach(c -> c.setElection(election));
      }

      Election saved = electionService.createElection(election, election.getCandidates());
      return ResponseEntity.ok(saved);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("Lỗi: " + e.getMessage());
    }
  }
  @PostMapping("/upload-single")
  public ResponseEntity<?> uploadSingleFile(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadFile(file);
    return ResponseEntity.ok(java.util.Map.of("url", url));
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
  @PostMapping("/create-json")
  public ResponseEntity<?> createByJson(@RequestBody Election election) {
    System.out.println("Role ID nhận được: " + election.getRoleId());

    if (election.getCandidates() != null) {
      election.getCandidates().forEach(c -> c.setElection(election));
    }

    LocalDateTime now = LocalDateTime.now();
    if (election.getStartTime() != null && now.isBefore(election.getStartTime())) {
      election.setStatus("UPCOMING");
    } else if (election.getEndTime() != null && now.isAfter(election.getEndTime())) {
      election.setStatus("ENDED");
    } else {
      election.setStatus("OPEN");
    }

    Election saved = electionRepository.save(election);
    System.out.println("Link ảnh nhận được: " + election.getImageUrl());
    return ResponseEntity.ok(saved);
  }
  @GetMapping("/{id}")
  public ResponseEntity<ElectionResponse> getById(@PathVariable Long id) {
    Election e = electionService.getById(id);
    ElectionResponse dto = new ElectionResponse();
    dto.setId(e.getId());
    dto.setTitle(e.getTitle());
    dto.setDescription(e.getDescription());
    dto.setStatus(e.getStatus());
    dto.setStartDate(e.getStartTime());
    dto.setEndDate(e.getEndTime());
    dto.setRoleId(e.getRoleId());
    dto.setImage(e.getImageUrl());

    if (e.getCandidates() != null) {
      dto.setCandidates(e.getCandidates().stream().map(c ->
          CandidateResponse.builder()
              .id(c.getId())
              .name(c.getName())
              .description(c.getDescription())
              .imageUrl(c.getImageUrl())
              .electionId(e.getId())
              .build()
      ).collect(Collectors.toList()));
    }
    return ResponseEntity.ok(dto);
  }
}
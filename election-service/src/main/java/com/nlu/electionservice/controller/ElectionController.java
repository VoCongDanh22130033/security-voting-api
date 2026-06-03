package com.nlu.electionservice.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.dto.CreateElectionRequest;
import com.nlu.electionservice.dto.ElectionRequest;
import com.nlu.electionservice.dto.ElectionResponse;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.service.ElectionService;
import com.nlu.electionservice.service.CloudinaryService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
  @Autowired
  private ElectionRoundRepository roundRepository;
  @Autowired
  private CandidateRepository candidateRepository;
  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());


  @PostMapping("/upload-image")
  public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadFile(file);
    return ResponseEntity.ok(java.util.Map.of("url", url));
  }

  @GetMapping
  public ResponseEntity<List<ElectionResponse>> getAll() {
    List<Election> list = electionRepository.findAll(org.springframework.data.domain.Sort.by(
        org.springframework.data.domain.Sort.Direction.DESC, "id"));

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
  public ResponseEntity<?> createElection(@RequestBody CreateElectionRequest request) {
    try {
      Election newElection = electionService.createMultiRoundElectionWithCandidates(request);
      return ResponseEntity.ok(newElection);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
  @PostMapping("/upload-single")
  public ResponseEntity<?> uploadSingleFile(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadFile(file);
    return ResponseEntity.ok(java.util.Map.of("url", url));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CreateElectionRequest request) {
    try {
      Election updatedElection = electionService.updateMultiRoundElection(id, request);
      return ResponseEntity.ok(updatedElection);
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(500).body("Lỗi máy chủ: " + e.getMessage());
    }
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
    dto.setWinnerId(e.getWinnerId());
    
    List<ElectionRound> allRounds = roundRepository.findByElectionId(id);
    dto.setRounds(allRounds);

    Optional<ElectionRound> roundToFetchCandidatesFrom = Optional.empty();

    if ("OPEN".equals(e.getStatus())) {
        roundToFetchCandidatesFrom = allRounds.stream()
            .filter(r -> "OPEN".equals(r.getStatus()))
            .findFirst();
    } else if ("UPCOMING".equals(e.getStatus())) {
        roundToFetchCandidatesFrom = allRounds.stream()
            .filter(r -> "UPCOMING".equals(r.getStatus()))
            .min(Comparator.comparing(ElectionRound::getRoundNumber));
    } else { // CLOSED or other statuses
        // For closed elections, we might want to show candidates of the first round by default
        roundToFetchCandidatesFrom = allRounds.stream()
            .min(Comparator.comparing(ElectionRound::getRoundNumber));
    }

    if (roundToFetchCandidatesFrom.isPresent()) {
        ElectionRound round = roundToFetchCandidatesFrom.get();
        dto.setCurrentRoundId(round.getId());
        List<Candidate> candidates = candidateRepository.findAllByRoundId(round.getId());
        dto.setCandidates(candidates.stream().map(c ->
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

  @GetMapping("/{electionId}/rounds")
  public ResponseEntity<List<ElectionRound>> getRoundsByElection(@PathVariable Long electionId) {
    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    return ResponseEntity.ok(rounds);
  }

  @PostMapping("/{electionId}/rounds/{roundNumber}/process")
  @Transactional
  public ResponseEntity<?> processRound(@PathVariable Long electionId, @PathVariable Integer roundNumber) {
    try {
      Optional<com.nlu.electionservice.entity.ElectionRound> currentRoundOpt =
          roundRepository.findByElectionIdAndRoundNumber(electionId, roundNumber);

      if (currentRoundOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("Không tìm thấy vòng đấu yêu cầu.");
      }

      com.nlu.electionservice.entity.ElectionRound currentRound = currentRoundOpt.get();

      currentRound.setStatus("CLOSED");
      roundRepository.save(currentRound);

      electionService.processRoundAfterClose(electionId, currentRound.getId());

      Optional<com.nlu.electionservice.entity.ElectionRound> nextRoundOpt =
          roundRepository.findByElectionIdAndRoundNumber(electionId, roundNumber + 1);

      if (nextRoundOpt.isPresent()) {
        com.nlu.electionservice.entity.ElectionRound nextRound = nextRoundOpt.get();
        nextRound.setStatus("OPEN");
        roundRepository.save(nextRound);

        return ResponseEntity.ok(java.util.Map.of(
            "message", "Đã chốt kết quả Vòng " + roundNumber + " và kích hoạt mở cổng Vòng " + (roundNumber + 1),
            "nextRoundAvailable", true
        ));
      } else {
        Election election = electionService.getById(electionId);
        election.setStatus("ENDED");
        electionRepository.save(election);

        return ResponseEntity.ok(java.util.Map.of(
            "message", "Đã hoàn thành cuộc bầu cử toàn cục! Đây là vòng đấu cuối cùng.",
            "nextRoundAvailable", false
        ));
      }
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Lỗi kết chuyển vòng đấu: " + e.getMessage());
    }
  }

  @GetMapping("/{id}/results")
  public ResponseEntity<List<CandidateResponse>> getResults(@PathVariable Long id) {
    List<CandidateResponse> results = electionService.getElectionResults(id);
    return ResponseEntity.ok(results);
  }

  @PostMapping("/{id}/synchronize-votes")
  public ResponseEntity<?> synchronizeVotes(@PathVariable Long id) {
    electionService.synchronizeVoteCounts(id);
    return ResponseEntity.ok(java.util.Map.of("message", "Đồng bộ số phiếu thành công."));
  }
}
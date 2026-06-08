package com.nlu.electionservice.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.dto.CreateElectionRequest;
import com.nlu.electionservice.dto.ElectionRequest;
import com.nlu.electionservice.dto.ElectionResponse;
import com.nlu.electionservice.dto.RoundDetailDto;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.ElectionVoterRepository;
import com.nlu.electionservice.repository.VoterRepository;
import com.nlu.electionservice.service.ElectionService;
import com.nlu.electionservice.service.ElectionParticipantInviteService;
import com.nlu.electionservice.service.KafkaProducerService;
import com.nlu.electionservice.service.CloudinaryService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
  @Autowired
  private KafkaProducerService kafkaProducerService;
  @Autowired
  private ElectionVoterRepository electionVoterRepository;
  @Autowired
  private VoterRepository voterRepository;
  @Autowired
  private ElectionParticipantInviteService participantInviteService;
  
  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());


  @PostMapping("/upload-image")
  public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadFile(file);
    return ResponseEntity.ok(java.util.Map.of("url", url));
  }

  @GetMapping
  public ResponseEntity<List<ElectionResponse>> getAll(@RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    List<Election> list = electionRepository.findAll(org.springframework.data.domain.Sort.by(
        org.springframework.data.domain.Sort.Direction.DESC, "id"));

    Optional<Long> voterIdLookup = Optional.empty();
    if (userEmail != null && !userEmail.isBlank()) {
      voterIdLookup = voterRepository.findVoterIdByEmail(userEmail);
    }
    final Optional<Long> voterId = voterIdLookup;

    List<ElectionResponse> response = list.stream().map(e -> {
      ElectionResponse dto = new ElectionResponse();
      dto.setId(e.getId());
      dto.setTitle(e.getTitle());
      dto.setDescription(e.getDescription());
      dto.setStatus(e.getStatus());
      dto.setStartDate(e.getStartTime());
      dto.setEndDate(e.getEndTime());
      dto.setImage(e.getImageUrl());
      dto.setAudienceType(e.getAudienceType().name());
      dto.setTargetDepartmentIds(e.getTargetDepartments().stream().map(d -> d.getId()).collect(Collectors.toList()));
      dto.setTargetDepartmentNames(e.getTargetDepartments().stream().map(d -> d.getName()).collect(Collectors.toList()));
      List<ElectionRound> allRounds = roundRepository.findByElectionId(e.getId());
      resolveDisplayRound(e, allRounds).ifPresent(round -> {
        dto.setCurrentRoundId(round.getId());
        dto.setCurrentRoundNumber(round.getRoundNumber());
        dto.setCurrentRoundTitle(round.getTitle());
      });

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
    })
    .filter(dto -> voterId.isEmpty() || electionVoterRepository.countByElectionIdAndVoterId(dto.getId(), voterId.get()) > 0)
    .collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }

  @PostMapping("/create")
  public ResponseEntity<?> createElection(@RequestBody CreateElectionRequest request, @RequestHeader("X-User-Email") String userEmail) {
    try {
      Election newElection = electionService.createMultiRoundElectionWithCandidates(request);
      kafkaProducerService.sendAuditEvent(userEmail, "ELECTION_CREATED", "Tạo Cuộc Bầu Cử Mới với ID: " + newElection.getId());
      return ResponseEntity.ok(newElection);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping(value = "/create-with-participants", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public ResponseEntity<?> createElectionWithParticipants(
      @RequestPart("election") CreateElectionRequest request,
      @RequestPart("file") MultipartFile file,
      @RequestHeader("X-User-Email") String userEmail) {
    try {
      Election newElection = electionService.createMultiRoundElectionWithCandidates(request);
      List<String> errors = participantInviteService.importParticipants(newElection.getId(), file);
      if (!errors.isEmpty()) {
        throw new RuntimeException(String.join("\n", errors));
      }
      kafkaProducerService.sendAuditEvent(userEmail, "ELECTION_CREATED",
          "Tao cuoc bau cu moi va import nguoi tham gia voi ID: " + newElection.getId());
      return ResponseEntity.ok(newElection);
    } catch (Exception e) {
      org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/upload-single")
  public ResponseEntity<?> uploadSingleFile(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadFile(file);
    return ResponseEntity.ok(java.util.Map.of("url", url));
  }

  @PostMapping("/{id}/participants/import")
  public ResponseEntity<?> importParticipants(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    try {
      List<String> errors = participantInviteService.importParticipants(id, file);
      if (!errors.isEmpty()) {
        return ResponseEntity.badRequest().body(errors);
      }
      kafkaProducerService.sendAuditEvent(
          userEmail != null ? userEmail : "organizer",
          "ELECTION_PARTICIPANTS_IMPORTED",
          "Imported participant Excel for election ID: " + id);
      return ResponseEntity.ok("Da import danh sach nguoi tham gia va gui email moi.");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/invites/verify")
  public ResponseEntity<?> verifyInvite(@RequestBody Map<String, String> request) {
    try {
      return ResponseEntity.ok(participantInviteService.verifyInvite(
          request.get("token"),
          request.get("citizenId")));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CreateElectionRequest request, @RequestHeader("X-User-Email") String userEmail) {
    try {
      Election updatedElection = electionService.updateMultiRoundElection(id, request);
      kafkaProducerService.sendAuditEvent(userEmail, "ELECTION_UPDATED", "Updated election with ID: " + updatedElection.getId());
      return ResponseEntity.ok(updatedElection);
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(500).body("Lỗi máy chủ: " + e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id, @RequestHeader("X-User-Email") String userEmail) {
    electionService.deleteElection(id);
    kafkaProducerService.sendAuditEvent(userEmail, "ELECTION_DELETED", "Deleted election with ID: " + id);
    return ResponseEntity.noContent().build();
  }
  @PostMapping("/create-json")
  public ResponseEntity<?> createByJson(@RequestBody Election election) {
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
    dto.setImage(e.getImageUrl());
    dto.setWinnerId(e.getWinnerId());
    dto.setAudienceType(e.getAudienceType().name());
    dto.setTargetDepartmentIds(e.getTargetDepartments().stream().map(d -> d.getId()).collect(Collectors.toList()));
    dto.setTargetDepartmentNames(e.getTargetDepartments().stream().map(d -> d.getName()).collect(Collectors.toList()));
    
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

        roundToFetchCandidatesFrom = allRounds.stream()
            .min(Comparator.comparing(ElectionRound::getRoundNumber));
    }

    if (roundToFetchCandidatesFrom.isPresent()) {
        ElectionRound round = roundToFetchCandidatesFrom.get();
        dto.setCurrentRoundId(round.getId());
        dto.setCurrentRoundNumber(round.getRoundNumber());
        dto.setCurrentRoundTitle(round.getTitle());
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

  private Optional<ElectionRound> resolveDisplayRound(Election election, List<ElectionRound> rounds) {
    if (rounds == null || rounds.isEmpty()) {
      return Optional.empty();
    }

    String status = election.getStatus();
    if ("OPEN".equals(status)) {
      Optional<ElectionRound> openRound = rounds.stream()
          .filter(r -> "OPEN".equals(r.getStatus()))
          .min(Comparator.comparing(ElectionRound::getRoundNumber));
      if (openRound.isPresent()) {
        return openRound;
      }
    }

    if ("UPCOMING".equals(status)) {
      return rounds.stream()
          .filter(r -> "UPCOMING".equals(r.getStatus()))
          .min(Comparator.comparing(ElectionRound::getRoundNumber));
    }

    return rounds.stream()
        .filter(r -> !"CANCELLED".equals(r.getStatus()))
        .max(Comparator.comparing(ElectionRound::getRoundNumber));
  }

  @GetMapping("/{electionId}/rounds")
  public ResponseEntity<List<ElectionRound>> getRoundsByElection(@PathVariable Long electionId) {
    List<ElectionRound> rounds = roundRepository.findByElectionId(electionId);
    return ResponseEntity.ok(rounds);
  }

  @PostMapping("/{electionId}/rounds/{roundNumber}/process")
  @Transactional
  public ResponseEntity<?> processRound(
      @PathVariable Long electionId,
      @PathVariable Integer roundNumber,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
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
        boolean nextRoundStarted = nextRound.getStartTime() == null || !LocalDateTime.now().isBefore(nextRound.getStartTime());
        nextRound.setStatus(nextRoundStarted ? "OPEN" : "UPCOMING");
        roundRepository.save(nextRound);
        if (nextRoundStarted) {
          participantInviteService.sendRoundInvitations(electionId, nextRound.getRoundNumber());
        }
        kafkaProducerService.sendAuditEvent(userEmail != null ? userEmail : "unknown", "ELECTION_ROUND_PROCESSED",
            "Processed election ID " + electionId + ", round " + roundNumber + " and prepared round " + (roundNumber + 1));

        return ResponseEntity.ok(java.util.Map.of(
            "message", nextRoundStarted
                ? "Da chot ket qua Vong " + roundNumber + " va mo Vong " + (roundNumber + 1)
                : "Da chot ket qua Vong " + roundNumber + ". Vong " + (roundNumber + 1) + " se tu dong mo dung thoi gian bat dau.",
            "nextRoundAvailable", true
        ));
      } else {
        Election election = electionService.getById(electionId);
        election.setStatus("ENDED");
        electionRepository.save(election);
        kafkaProducerService.sendAuditEvent(userEmail != null ? userEmail : "unknown", "ELECTION_COMPLETED",
            "Processed final round " + roundNumber + " for election ID " + electionId);

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

  @GetMapping("/{id}/rounds-details")
  public ResponseEntity<List<RoundDetailDto>> getRoundDetails(@PathVariable Long id) {
      List<RoundDetailDto> roundDetails = electionService.getElectionDetailsWithRounds(id);
      return ResponseEntity.ok(roundDetails);
  }
}

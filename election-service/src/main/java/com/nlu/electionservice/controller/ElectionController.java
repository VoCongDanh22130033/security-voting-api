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
import com.nlu.electionservice.entity.ElectionVoterInvite;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.ElectionVoterInviteRepository;
import com.nlu.electionservice.repository.ElectionVoterRepository;
import com.nlu.electionservice.repository.RoundCandidateRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.repository.VoterRepository;
import com.nlu.electionservice.service.ElectionService;
import com.nlu.electionservice.service.ElectionReportService;
import com.nlu.electionservice.service.ElectionParticipantInviteService;
import com.nlu.electionservice.service.KafkaProducerService;
import com.nlu.electionservice.service.CloudinaryService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequestMapping("/api/elections")
public class ElectionController {

  @Value("${app.crypto-service-url:http://localhost:8084}")
  private String cryptoServiceUrl;

  @Value("${app.internal-service-token}")
  private String internalToken;

  private final RestTemplate restTemplate = new RestTemplate();

  @Autowired
  private CloudinaryService cloudinaryService;
  @Autowired
  private ElectionService electionService;
  @Autowired
  private ElectionReportService electionReportService;
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
  @Autowired
  private ElectionVoterInviteRepository inviteRepository;
  @Autowired
  private BlindSignatureLogRepository blindSignatureLogRepository;
  @Autowired
  private com.nlu.electionservice.repository.UserRepository userRepository;
  @Autowired
  private VoteRepository voteRepository;
  @Autowired
  private RoundCandidateRepository roundCandidateRepository;
  
  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());


  @PostMapping("/upload-image")
  public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadFile(file);
    return ResponseEntity.ok(java.util.Map.of("url", url));
  }

  @GetMapping
  public ResponseEntity<List<ElectionResponse>> getAll(
      @RequestHeader(value = "X-User-Email", required = false) String userEmail,
      @RequestHeader(value = "X-User-Role", required = false) String userRole) {
    List<Election> list = electionRepository.findAll(org.springframework.data.domain.Sort.by(
        org.springframework.data.domain.Sort.Direction.DESC, "id"));

    // Organizer/Admin thấy tất cả elections, không lọc theo voter
    boolean isPrivileged = userRole != null &&
        (userRole.contains("ROLE_ORGANIZER") || userRole.contains("ROLE_ADMIN"));

    Optional<Long> voterIdLookup = Optional.empty();
    if (!isPrivileged && userEmail != null && !userEmail.isBlank()) {
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
      dto.setRoleId(e.getRoleId());
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
                .party(c.getParty())
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
      Long creatorId = userRepository.findByEmail(userEmail).map(u -> u.getId()).orElse(null);
      Election newElection = electionService.createMultiRoundElectionWithCandidates(request, creatorId);
      generateElectionKey(newElection.getId());
      kafkaProducerService.sendAuditEvent(userEmail, "ELECTION_CREATED", "Tạo cuộc bầu cử mới với ID: " + newElection.getId());
      return ResponseEntity.ok(newElection);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping(value = "/create-with-participants", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public ResponseEntity<?> createElectionWithParticipants(
      @RequestPart("election") MultipartFile electionPart,
      @RequestPart(value = "file", required = false) MultipartFile file,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    try {
      CreateElectionRequest request = mapper.readValue(electionPart.getBytes(), CreateElectionRequest.class);
      Long creatorId = userEmail != null ? userRepository.findByEmail(userEmail).map(u -> u.getId()).orElse(null) : null;
      Election newElection = electionService.createMultiRoundElectionWithCandidates(request, creatorId);
      generateElectionKey(newElection.getId());
      if (file != null && !file.isEmpty()) {
        List<String> errors = participantInviteService.importParticipants(newElection.getId(), file);
        if (!errors.isEmpty()) {
          throw new RuntimeException(String.join("\n", errors));
        }
      }
      kafkaProducerService.sendAuditEvent(userEmail != null ? userEmail : "organizer", "ELECTION_CREATED",
          "Tạo cuộc bầu cử và nhập danh sách người tham gia với ID: " + newElection.getId());
      return ResponseEntity.ok(newElection);
    } catch (Exception e) {
      org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /**
   * Kiểm tra quyền sở hữu election dựa trên role_id (userId của người tạo).
   * Admin bypass. Dữ liệu cũ (roleId = null) cho phép mọi organizer.
   */
  private ResponseEntity<?> checkOwner(Long electionId, String userEmail) {
    if (userEmail == null || userEmail.isBlank()) return null;
    if (userEmail.contains("admin")) return null;
    Election el = electionRepository.findById(electionId).orElse(null);
    if (el == null) return null;
    if (el.getRoleId() == null) return null; // dữ liệu cũ, cho qua
    var userOpt = userRepository.findByEmail(userEmail);
    if (userOpt.isEmpty()) return null;
    if (!el.getRoleId().equals(userOpt.get().getId())) {
      return ResponseEntity.status(403).body("Bạn không có quyền thực hiện thao tác này. Chỉ người tạo cuộc bầu cử mới có quyền.");
    }
    return null;
  }

  private void generateElectionKey(Long electionId) {
    try {
      org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
      h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
      h.set("X-Internal-Token", internalToken);
      restTemplate.postForEntity(
          cryptoServiceUrl + "/api/crypto/election-keys/generate",
          new org.springframework.http.HttpEntity<>(Map.of("electionId", electionId), h),
          Map.class);
      log.info(">>> [Election] Đã sinh key pair cho election {}.", electionId);
    } catch (Exception e) {
      log.warn(">>> [Election] Không thể sinh key pair cho election {}: {}", electionId, e.getMessage());
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
    ResponseEntity<?> ownerErr = checkOwner(id, userEmail);
    if (ownerErr != null) return ownerErr;
    try {
      List<String> errors = participantInviteService.importParticipants(id, file);
      if (!errors.isEmpty()) {
        return ResponseEntity.badRequest().body(errors);
      }
      kafkaProducerService.sendAuditEvent(
          userEmail != null ? userEmail : "organizer",
          "ELECTION_PARTICIPANTS_IMPORTED",
          "Nhập danh sách người tham gia cho cuộc bầu cử ID: " + id);
      return ResponseEntity.ok("Đã import danh sách người tham gia và gửi email mời.");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/{id}/participants/dashboard")
  public ResponseEntity<?> getParticipantDashboard(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(participantInviteService.getParticipantDashboard(id));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/{id}/participants/invites")
  public ResponseEntity<?> getParticipantInvites(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(participantInviteService.listParticipantInvites(id));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{id}/participants/resend-all-not-voted")
  public ResponseEntity<?> resendAllNotVoted(
      @PathVariable Long id,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    try {
      Map<String, Object> result = participantInviteService.resendAllNotVoted(id);
      kafkaProducerService.sendAuditEvent(
          userEmail != null ? userEmail : "organizer",
          "ELECTION_INVITE_RESENT",
          "Gửi lại QR cho tất cả cử tri chưa bỏ phiếu, cuộc bầu cử ID: " + id + " — đã gửi: " + result.get("sent"));
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{id}/participants/{inviteId}/resend")
  public ResponseEntity<?> resendParticipantInvite(
      @PathVariable Long id,
      @PathVariable Long inviteId,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    try {
      Map<String, Object> result = participantInviteService.resendInvitation(id, inviteId);
      kafkaProducerService.sendAuditEvent(
          userEmail != null ? userEmail : "organizer",
          "ELECTION_INVITE_RESENT",
          "Gửi lại lời mời ID " + inviteId + " cho cuộc bầu cử ID: " + id);
      return ResponseEntity.ok(result);
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

  @PostMapping("/invites/verify-internal")
  public ResponseEntity<?> verifyInviteInternal(@RequestBody Map<String, Object> request) {
    try {
      String token = (String) request.get("token");
      Long electionId = request.get("electionId") != null ? Long.valueOf(request.get("electionId").toString()) : null;
      Long roundId = request.get("roundId") != null ? Long.valueOf(request.get("roundId").toString()) : null;

      ElectionVoterInvite invite = participantInviteService.resolveVerifiedInvite(token, electionId, roundId);

      return ResponseEntity.ok(Map.of(
          "voterId", invite.getVoterId(),
          "email", invite.getEmail()
      ));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CreateElectionRequest request, @RequestHeader("X-User-Email") String userEmail) {
    ResponseEntity<?> ownerErr = checkOwner(id, userEmail);
    if (ownerErr != null) return ownerErr;
    try {
      Election updatedElection = electionService.updateMultiRoundElection(id, request);
      kafkaProducerService.sendAuditEvent(userEmail, "ELECTION_UPDATED", "Cập nhật cuộc bầu cử ID: " + updatedElection.getId());
      return ResponseEntity.ok(updatedElection);
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(500).body("Lỗi máy chủ: " + e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id, @RequestHeader("X-User-Email") String userEmail) {
    ResponseEntity<?> ownerErr = checkOwner(id, userEmail);
    if (ownerErr != null) return ownerErr;
    electionService.deleteElection(id);
    kafkaProducerService.sendAuditEvent(userEmail, "ELECTION_DELETED", "Xóa cuộc bầu cử ID: " + id);
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
    log.info("Link anh nhan duoc: {}", election.getImageUrl());
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
                .party(c.getParty())
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
    ResponseEntity<?> ownerErr = checkOwner(electionId, userEmail);
    if (ownerErr != null) return ownerErr;
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
        kafkaProducerService.sendAuditEvent(userEmail != null ? userEmail : "unknown", "ELECTION_ROUND_PROCESSED",
            "Chốt kết quả vòng " + roundNumber + " và chuẩn bị vòng " + (roundNumber + 1) + " cho cuộc bầu cử ID: " + electionId);

        return ResponseEntity.ok(java.util.Map.of(
            "message", nextRoundStarted
                ? "Đã chốt kết quả Vòng " + roundNumber + " và mở Vòng " + (roundNumber + 1)
                : "Đã chốt kết quả Vòng " + roundNumber + ". Vòng " + (roundNumber + 1) + " sẽ tự động mở đúng thời gian bắt đầu.",
            "nextRoundAvailable", true
        ));
      } else {
        Election election = electionService.getById(electionId);
        election.setStatus("CLOSED");
        electionRepository.save(election);
        kafkaProducerService.sendAuditEvent(userEmail != null ? userEmail : "unknown", "ELECTION_COMPLETED",
            "Hoàn tất vòng cuối " + roundNumber + " cho cuộc bầu cử ID: " + electionId);

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
  public ResponseEntity<?> synchronizeVotes(@PathVariable Long id, @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
    ResponseEntity<?> ownerErr = checkOwner(id, userEmail);
    if (ownerErr != null) return ownerErr;
    electionService.synchronizeVoteCounts(id);
    return ResponseEntity.ok(java.util.Map.of("message", "Đồng bộ số phiếu thành công."));
  }

  @GetMapping("/{id}/rounds-details")
  public ResponseEntity<List<RoundDetailDto>> getRoundDetails(@PathVariable Long id) {
      List<RoundDetailDto> roundDetails = electionService.getElectionDetailsWithRounds(id);
      return ResponseEntity.ok(roundDetails);
  }

  @GetMapping("/{id}/reports/excel")
  public ResponseEntity<byte[]> exportExcelReport(
      @PathVariable Long id,
      @RequestHeader(value = "X-User-Email", required = false) String exportedBy) {
    try {
      byte[] bytes = electionReportService.exportExcel(id, exportedBy);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=election-" + id + "-report.xlsx")
          .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(bytes);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  @GetMapping("/{id}/admin-stats")
  public ResponseEntity<?> getAdminStats(@PathVariable Long id) {
    try {
      Election election = electionRepository.findById(id)
          .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));

      // Thông tin chung
      Map<String, Object> info = new java.util.LinkedHashMap<>();
      info.put("id", election.getId());
      info.put("title", election.getTitle());
      info.put("description", election.getDescription());
      info.put("status", election.getStatus());
      info.put("startTime", election.getStartTime() != null ? election.getStartTime().toString() : null);
      info.put("endTime", election.getEndTime() != null ? election.getEndTime().toString() : null);
      info.put("createdAt", election.getCreatedAt() != null ? election.getCreatedAt().toString() : null);
      info.put("totalRounds", election.getTotalRounds());
      info.put("imageUrl", election.getImageUrl());

      // Organizer: lấy từ audit log nếu có, hoặc bỏ trống
      info.put("organizer", "—");

      // Thống kê cử tri (dùng DISTINCT voter_id để tránh đếm trùng qua nhiều vòng)
      long totalInvited = inviteRepository.countInvitedByElectionId(id);
      long totalVerified = inviteRepository.countVerifiedByElectionId(id);
      long totalVoted = blindSignatureLogRepository.countDistinctVotedByElectionId(id);
      long notVoted = totalInvited - totalVoted;
      double participationRate = totalInvited > 0 ? Math.round(totalVoted * 1000.0 / totalInvited) / 10.0 : 0.0;

      Map<String, Object> voterStats = new java.util.LinkedHashMap<>();
      voterStats.put("totalInvited", totalInvited);
      voterStats.put("totalVerified", totalVerified);
      voterStats.put("totalVoted", totalVoted);
      voterStats.put("notVoted", notVoted);
      voterStats.put("participationRate", participationRate);

      // Thống kê phiếu bầu
      long totalRecorded = voteRepository.countByElectionId(id);
      long totalValid = voteRepository.countByElectionIdAndCandidateIdIsNotNull(id);
      long totalInvalid = totalRecorded - totalValid;
      java.time.LocalDateTime firstVote = blindSignatureLogRepository.findFirstVoteTime(id);
      java.time.LocalDateTime lastVote = blindSignatureLogRepository.findLastVoteTime(id);

      Map<String, Object> ballotStats = new java.util.LinkedHashMap<>();
      ballotStats.put("totalRecorded", totalRecorded);
      ballotStats.put("totalValid", totalValid);
      ballotStats.put("totalInvalid", totalInvalid);
      ballotStats.put("firstVoteTime", firstVote != null ? firstVote.toString() : null);
      ballotStats.put("lastVoteTime", lastVote != null ? lastVote.toString() : null);

      // Thống kê theo vòng
      List<com.nlu.electionservice.entity.ElectionRound> rounds = roundRepository.findByElectionId(id);
      rounds.sort(java.util.Comparator.comparing(com.nlu.electionservice.entity.ElectionRound::getRoundNumber));
      List<Map<String, Object>> roundStats = rounds.stream().map(r -> {
        Map<String, Object> rs = new java.util.LinkedHashMap<>();
        rs.put("roundId", r.getId());
        rs.put("roundNumber", r.getRoundNumber());
        rs.put("title", r.getTitle() != null ? r.getTitle() : "Vòng " + r.getRoundNumber());
        rs.put("startTime", r.getStartTime() != null ? r.getStartTime().toString() : null);
        rs.put("endTime", r.getEndTime() != null ? r.getEndTime().toString() : null);
        rs.put("status", r.getStatus());
        long roundVotes = blindSignatureLogRepository.countDistinctVotedByElectionIdAndRoundId(id, r.getId());
        rs.put("totalVotes", roundVotes);
        long candidates = roundCandidateRepository.countByRoundId(r.getId());
        rs.put("totalCandidates", candidates);
        rs.put("maxAdvanceCount", r.getMaxAdvanceCount() != null ? r.getMaxAdvanceCount() : 0);
        return rs;
      }).collect(Collectors.toList());

      Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("info", info);
      result.put("voterStats", voterStats);
      result.put("ballotStats", ballotStats);
      result.put("rounds", roundStats);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
    }
  }

  @GetMapping("/my-elections")
  public ResponseEntity<?> getMyElections(@RequestParam("citizenId") String citizenId) {
    try {
      String normalized = citizenId == null ? "" : citizenId.trim().replaceAll("\\s+", "").replaceAll("^0+(?!$)", "");
      if (normalized.isBlank()) {
        return ResponseEntity.badRequest().body("Vui lòng nhập số CCCD.");
      }

      // Kiểm tra tài khoản có bị khóa không
      Optional<com.nlu.electionservice.entity.Voter> voterOpt = voterRepository.findByCitizenId(normalized);
      if (voterOpt.isEmpty()) {
        voterOpt = voterRepository.findByCitizenId(citizenId.trim());
      }
      if (voterOpt.isPresent()) {
        com.nlu.electionservice.entity.User u = voterOpt.get().getUser();
        if (u != null && u.getIsLock() != null && u.getIsLock() == 1) {
          return ResponseEntity.status(403).body("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
      }

      // Tìm tất cả electionId mà citizenId này được mời tham gia
      List<Long> electionIds = inviteRepository.findElectionIdsByCitizenId(normalized);
      // Fallback: thử tìm thêm với leading zero
      if (electionIds.isEmpty()) {
        electionIds = inviteRepository.findElectionIdsByCitizenId(citizenId.trim());
      }
      if (electionIds.isEmpty()) {
        return ResponseEntity.ok(List.of());
      }
      List<Election> elections = electionRepository.findAllById(electionIds);
      List<Map<String, Object>> result = elections.stream().map(e -> {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", e.getId());
        m.put("title", e.getTitle());
        m.put("description", e.getDescription());
        m.put("status", e.getStatus() != null ? e.getStatus() : "UNKNOWN");
        m.put("image", e.getImageUrl());
        m.put("startDate", e.getStartTime() != null ? e.getStartTime().toString() : null);
        m.put("endTime", e.getEndTime() != null ? e.getEndTime().toString() : null);
        return m;
      }).collect(Collectors.toList());
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
    }
  }

  @GetMapping("/{id}/reports/pdf")
  public ResponseEntity<byte[]> exportPdfReport(@PathVariable Long id) {
    try {
      byte[] bytes = electionReportService.exportPdf(id);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=election-" + id + "-report.pdf")
          .contentType(MediaType.APPLICATION_PDF)
          .body(bytes);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }
}
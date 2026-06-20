package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.VoteAnonymousRequest;
import com.nlu.electionservice.dto.VoteE2ERequest;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionVoterInvite;
import com.nlu.electionservice.entity.User;
import com.nlu.electionservice.repository.AnonymousVoteRepository;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.ElectionVoterRepository;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.RoundCandidateRepository;
import com.nlu.electionservice.repository.UserRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.service.ElectionParticipantInviteService;
import com.nlu.electionservice.service.ElectionService;
import com.nlu.electionservice.service.KafkaProducerService;
import com.nlu.electionservice.service.RealtimeNotificationService;
import com.nlu.electionservice.service.VoteService;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequestMapping("/api/v1/votes")
public class VoteController {

  @Autowired
  private ElectionService electionService;

  @Autowired
  private VoteService voteService;

  @Autowired
  private VoteRepository voteRepository;

  @Autowired
  private ElectionRoundRepository roundRepository;

  @Autowired
  private ElectionRepository electionRepository;

  @Autowired
  private AnonymousVoteRepository anonymousVoteRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private BlindSignatureLogRepository blindSignatureLogRepository;

  @Autowired
  private ElectionVoterRepository electionVoterRepository;

  @Autowired
  private RoundCandidateRepository roundCandidateRepository;

  @Autowired
  private CandidateRepository candidateRepository;

  @Autowired
  private ElectionRoundRepository electionRoundRepository;

  @Autowired
  private RealtimeNotificationService realtimeNotificationService;

  @Autowired
  private ElectionParticipantInviteService participantInviteService;

  @Autowired
  private KafkaProducerService kafkaProducerService;

  @Value("${app.load-test-skip-cccd:false}")
  private boolean loadTestSkipCccd;

  @Value("${app.crypto-sign-enabled:true}")
  private boolean cryptoSignEnabled;

  private final RestTemplate restTemplate = createRestTemplate();

  @Value("${app.crypto-public-key-url:http://localhost:8084/api/crypto/public-key}")
  private String cryptoPublicKeyUrl;

  @Value("${app.crypto-service-url:http://localhost:8084}")
  private String cryptoServiceUrl;

  @Value("${app.crypto-sign-url:http://localhost:8084/api/crypto/sign-e2e}")
  private String cryptoSignUrl;

  @Value("${app.internal-service-token}")
  private String internalToken;

  private HttpHeaders internalHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.set("X-Internal-Token", internalToken);
    return h;
  }

  // Cache RSA public key to avoid fetching on every vote request
  private final AtomicReference<BigInteger> cachedN = new AtomicReference<>();
  private final AtomicReference<BigInteger> cachedE = new AtomicReference<>();

  private static RestTemplate createRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return new RestTemplate(factory);
  }

  // Cache per-election public keys
  private final ConcurrentHashMap<Long, BigInteger[]> electionKeyCache = new java.util.concurrent.ConcurrentHashMap<>();

  private BigInteger[] getPublicKey(Long electionId) {
    if (electionId != null) {
      return electionKeyCache.computeIfAbsent(electionId, id -> fetchPublicKey(cryptoPublicKeyUrl + "?electionId=" + id));
    }
    BigInteger n = cachedN.get();
    BigInteger e = cachedE.get();
    if (n == null || e == null) {
      BigInteger[] kp = fetchPublicKey(cryptoPublicKeyUrl);
      cachedN.set(kp[0]); cachedE.set(kp[1]);
      return kp;
    }
    return new BigInteger[]{n, e};
  }

  private BigInteger[] fetchPublicKey(String url) {
    ResponseEntity<Map> pkRes = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, new HttpEntity<>(internalHeaders()), Map.class);
    if (pkRes.getBody() == null) throw new RuntimeException("Không thể lấy RSA public key.");
    return new BigInteger[]{
        new BigInteger(pkRes.getBody().get("modulus").toString().trim(), 16),
        new BigInteger(pkRes.getBody().get("exponent").toString().trim(), 16)
    };
  }

  /** @deprecated dùng getPublicKey(electionId) */
  private BigInteger[] getPublicKey() { return getPublicKey(null); }

  @PostMapping("/cast-e2e")
  public ResponseEntity<?> castE2EVote(
      @RequestBody VoteE2ERequest request,
      @RequestHeader(value = "X-User-Email", required = false) String email) {
    try {
      User user;
      String auditEmail = email;
      if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
        if (loadTestSkipCccd) {
          // Load test mode: bỏ qua verifiedAt, chỉ cần invite token tồn tại và đúng election/round
          ElectionVoterInvite invite = participantInviteService.resolveInviteForLoadTest(
              request.getInviteToken(),
              request.getElectionId(),
              request.getRoundId());
          user = userRepository.findById(invite.getVoterId())
              .orElseThrow(() -> new RuntimeException("Người tham gia không hợp lệ."));
          auditEmail = invite.getEmail();
        } else {
          ElectionVoterInvite invite = participantInviteService.resolveVerifiedInvite(
              request.getInviteToken(),
              request.getElectionId(),
              request.getRoundId());
          user = userRepository.findById(invite.getVoterId())
              .orElseThrow(() -> new RuntimeException("Người tham gia không hợp lệ."));
          auditEmail = invite.getEmail();
        }
      } else {
        if (email == null || email.isBlank()) {
          return ResponseEntity.status(401).body("Vui lòng đăng nhập hoặc xác thực bằng link mời.");
        }
        user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Người dùng không hợp lệ."));
      }

      // Kiểm tra tài khoản bị khóa
      if (user.getIsLock() != null && user.getIsLock() == 1) {
        return ResponseEntity.status(403).body("Tài khoản của bạn đã bị khóa. Không thể tham gia bầu cử.");
      }

      if (!loadTestSkipCccd &&
          electionVoterRepository.countByElectionIdAndVoterId(request.getElectionId(), user.getId()) == 0) {
        return ResponseEntity.status(403).body("Bạn không nằm trong danh sách được tham gia cuộc bầu cử này.");
      }

      // Kiểm tra vòng bầu cử có đang OPEN không
      var roundOpt = roundRepository.findById(request.getRoundId());
      if (roundOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("Vòng bầu cử không tồn tại.");
      }
      String roundStatus = roundOpt.get().getStatus();
      if (!"OPEN".equals(roundStatus)) {
        String msg = "UPCOMING".equals(roundStatus)
            ? "Vòng bầu cử chưa bắt đầu. Vui lòng chờ đến thời gian bắt đầu."
            : "Vòng bầu cử đã kết thúc. Không thể bỏ phiếu.";
        return ResponseEntity.status(403).body(msg);
      }

      boolean hasVoted = blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(
          user.getId(),
          request.getElectionId(),
          request.getRoundId());

      if (hasVoted) {
        return ResponseEntity.badRequest().body("Bạn đã bỏ phiếu cho vòng này rồi.");
      }

      // Chỉ validate candidateId khi không dùng E2E encryption
      if (request.getEncryptedVote() == null || request.getEncryptedVote().isBlank()) {
        boolean validCandidate = roundCandidateRepository.countCandidateInElectionRound(
            request.getElectionId(),
            request.getRoundId(),
            request.getCandidateId()) > 0;
        if (!validCandidate) {
          return ResponseEntity.badRequest().body("Ứng viên không thuộc vòng bầu cử này.");
        }
      }

      // ── Xác minh chữ ký mù (Blind Signature Verification) ─────────────────
      // Cử tri đã thực hiện: blindedMsg = M * r^E mod N → /api/crypto/sign → S_blind → S = S_blind * r^-1 mod N
      // Tại đây server chỉ cần xác minh: S^E mod N == M (không biết cử tri chọn ai khi ký)
      BigInteger signedToken;

      if (cryptoSignEnabled) {
        // Chế độ sản xuất: xác minh RSA blind signature
        // Client phải gửi messageToken (M) và blindSignature (S đã unblind)
        String messageTokenStr = request.getBlindToken(); // M: hex của message gốc
        String signatureStr    = request.getBlindSignature(); // S: hex của chữ ký đã unblind

        if (messageTokenStr == null || messageTokenStr.isBlank()
            || signatureStr == null || signatureStr.isBlank()) {
          return ResponseEntity.badRequest().body(
              "Chế độ xác thực RSA yêu cầu cả messageToken (blindToken) và blindSignature.");
        }

        // Lấy RSA public key theo election (cached sau lần đầu tiên)
        BigInteger[] pk = getPublicKey(request.getElectionId());
        BigInteger N = pk[0];
        BigInteger E = pk[1];
        BigInteger M = new BigInteger(messageTokenStr.trim(), 16);
        BigInteger S = new BigInteger(signatureStr.trim(), 16);

        // Xác minh: S^E mod N == M (server không biết ai ký, chỉ xác minh chữ ký hợp lệ)
        if (!S.modPow(E, N).equals(M)) {
          return ResponseEntity.badRequest().body("Chữ ký mù không hợp lệ. Lá phiếu bị từ chối.");
        }
        signedToken = S;
      } else {
        // Chế độ test (crypto-sign-enabled=false): bỏ qua RSA, dùng random token
        // blindToken là hex bất kỳ từ client (ví dụ SHA-256 ngẫu nhiên từ k6)
        String tokenStr = request.getBlindToken();
        if (tokenStr == null || tokenStr.isBlank()) {
          return ResponseEntity.badRequest().body("blindToken không được rỗng.");
        }
        // Token ẩn danh: không chứa userId, chỉ hash của blindToken + nonce ngẫu nhiên
        signedToken = new BigInteger(1, java.security.MessageDigest.getInstance("SHA-256")
            .digest((tokenStr + ":" + java.util.UUID.randomUUID()).getBytes(
                java.nio.charset.StandardCharsets.UTF_8)));
      }

      // ── Lưu log + phiếu trong cùng 1 transaction — nếu fail thì cả 2 rollback ──
      String receiptCode = voteService.markAndCastVote(user.getId(), request, signedToken);

      // Gửi Kafka audit event VOTE_CASTED
      kafkaProducerService.sendAuditEvent(
          email,
          "VOTE_CASTED",
          "electionId=" + request.getElectionId() + ", roundId=" + request.getRoundId() + ", receipt=" + receiptCode);

      // Broadcast kết quả realtime sau khi vote được lưu
      try {
        List<Map<String, Object>> stats = voteRepository.countVotesByCandidate(
            request.getElectionId(), request.getRoundId());
        realtimeNotificationService.voteCountUpdated(
            request.getElectionId(), request.getRoundId(), stats);
      } catch (Exception ex) {
        log.error("[cast-e2e] Realtime broadcast lỗi: {}", ex.getMessage());
      }

      return ResponseEntity.ok(Map.of(
          "message", "Bỏ phiếu thành công. Lá phiếu của bạn đã được ghi lại ẩn danh.",
          "receiptCode", receiptCode));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Lỗi trong quá trình bỏ phiếu: " + e.getMessage());
    }
  }

  @PostMapping("/submit-anonymous")
  public ResponseEntity<?> submitVote(@RequestBody VoteAnonymousRequest request) {
    try {
      log.info(">>> [BE Election] Nhận yêu cầu bỏ phiếu ẩn danh cho RoundID: {}", request.getRoundId());

      ResponseEntity<Map<String, String>> response = restTemplate.exchange(
          cryptoPublicKeyUrl,
          HttpMethod.GET,
          new HttpEntity<>(internalHeaders()),
          new ParameterizedTypeReference<Map<String, String>>() {});
      Map<String, String> keyParams = response.getBody();

      if (keyParams == null || !keyParams.containsKey("modulus") || !keyParams.containsKey("exponent")) {
        throw new RuntimeException("Không thể lấy khóa bảo mật hệ thống từ dịch vụ mã hóa.");
      }

      BigInteger n = new BigInteger(keyParams.get("modulus").trim(), 16);
      BigInteger e = new BigInteger(keyParams.get("exponent").trim(), 16);

      electionService.submitAnonymousVote(request, n, e);

      return ResponseEntity.ok(Map.of("message", "Bỏ phiếu thành công. Phiếu đã được lưu ẩn danh."));
    } catch (Exception e) {
      log.error(">>> [BE Election ERROR] Lỗi xử lý hòm phiếu: {}", e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/count")
  public ResponseEntity<?> countVotesByRound(
      @RequestParam("electionId") Long electionId,
      @RequestParam("roundId") Long roundId) {
    long count = voteRepository.countByElectionIdAndRoundId(electionId, roundId);
    return ResponseEntity.ok(Map.of("totalVotes", count));
  }

  @GetMapping("/results")
  public ResponseEntity<?> getElectionResults(
      @RequestParam("electionId") Long electionId,
      @RequestParam("roundId") Long roundId) {
    try {
      log.info(">>> [BE Election] Tổng hợp kết quả hòm phiếu cho ElectionID: {}, RoundID: {}", electionId, roundId);

      List<Map<String, Object>> rawStats = voteRepository.countVotesByCandidate(electionId, roundId);
      List<Map<String, Object>> voteStats = new java.util.ArrayList<>();

      if (rawStats != null) {
        for (Map<String, Object> row : rawStats) {
          Map<String, Object> cleanRow = new java.util.HashMap<>();
          cleanRow.put("candidate_id", row.get("candidateId"));
          cleanRow.put("vote_count", row.get("voteCount"));
          voteStats.add(cleanRow);
        }
      }

      Map<String, Object> cleanElectionInfo = new java.util.HashMap<>();
      String roundTitleDisplay = "Bầu cử vòng " + roundId;

      try {
        Optional<Election> electionOpt = electionRepository.findById(electionId);
        if (electionOpt.isPresent()) {
          Election election = electionOpt.get();
          cleanElectionInfo.put("id", election.getId());
          cleanElectionInfo.put("title", election.getTitle());
          cleanElectionInfo.put("description", election.getDescription());
          cleanElectionInfo.put("status", election.getStatus() != null ? election.getStatus().toString() : "CLOSED");
          cleanElectionInfo.put("winnerId", election.getWinnerId());
          cleanElectionInfo.put("totalRounds", election.getTotalRounds());

          var roundOpt = roundRepository.findById(roundId);
          if (roundOpt.isPresent() && roundOpt.get().getTitle() != null) {
            roundTitleDisplay = roundOpt.get().getTitle();
          }
        } else {
          cleanElectionInfo.put("id", electionId);
          cleanElectionInfo.put("title", "Cuộc bầu cử số #" + electionId);
          cleanElectionInfo.put("status", "CLOSED");
        }
      } catch (Exception ex) {
        cleanElectionInfo.put("id", electionId);
        cleanElectionInfo.put("title", "Cuộc bầu cử số #" + electionId);
        cleanElectionInfo.put("status", "CLOSED");
      }

      Map<String, Object> responsePayload = new java.util.HashMap<>();
      responsePayload.put("election", cleanElectionInfo);
      responsePayload.put("roundTitle", roundTitleDisplay);
      responsePayload.put("votes", voteStats);

      return ResponseEntity.ok(responsePayload);
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Lỗi xử lý kết quả kiểm phiếu: " + e.getMessage());
    }
  }

  @GetMapping("/announcement/{electionId}")
  public ResponseEntity<?> getOfficialAnnouncement(@PathVariable Long electionId) {
    try {
      Optional<Election> electionOpt = electionRepository.findById(electionId);
      if (electionOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("Không tìm thấy cuộc bầu cử.");
      }

      Election election = electionOpt.get();
      if (!("CLOSED".equals(election.getStatus()) || "ENDED".equals(election.getStatus()))) {
        return ResponseEntity.badRequest().body("Cuộc bầu cử chưa kết thúc.");
      }

      Long winnerId = election.getWinnerId();
      if (winnerId == null) {
        return ResponseEntity.badRequest().body("Cuộc bầu cử chưa có kết quả chính thức.");
      }

      var finalRoundOpt = roundRepository.findByElectionIdAndRoundNumber(electionId, election.getTotalRounds());
      if (finalRoundOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("Không tìm thấy vòng cuối cùng.");
      }

      Long finalRoundId = finalRoundOpt.get().getId();
      List<Map<String, Object>> rawStats = voteRepository.countVotesByCandidate(electionId, finalRoundId);
      List<java.util.Map.Entry<Long, Long>> candidateCounts = new java.util.ArrayList<>();

      if (rawStats != null) {
        for (Map<String, Object> row : rawStats) {
          Object cid = row.get("candidateId");
          Object vc = row.get("voteCount");
          Long candidateId = cid instanceof Number ? ((Number) cid).longValue() : Long.parseLong(cid.toString());
          Long voteCount = vc instanceof Number ? ((Number) vc).longValue() : Long.parseLong(vc.toString());
          candidateCounts.add(new java.util.AbstractMap.SimpleEntry<>(candidateId, voteCount));
        }
      }

      candidateCounts.sort((a, b) -> {
        int cmp = b.getValue().compareTo(a.getValue());
        if (cmp != 0) {
          return cmp;
        }
        return a.getKey().compareTo(b.getKey());
      });

      List<Map<String, Object>> topThree = new java.util.ArrayList<>();
      java.util.stream.IntStream.range(0, Math.min(3, candidateCounts.size()))
          .forEach(i -> {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("rank", i + 1);
            entry.put("candidate_id", candidateCounts.get(i).getKey());
            entry.put("vote_count", candidateCounts.get(i).getValue());
            topThree.add(entry);
          });

      Map<String, Object> announcement = new java.util.HashMap<>();
      announcement.put("election_id", electionId);
      announcement.put("election_title", election.getTitle());
      announcement.put("election_status", election.getStatus());
      announcement.put("winner_id", winnerId);
      announcement.put("top_candidates", topThree);
      announcement.put("announcement_time", java.time.LocalDateTime.now());

      return ResponseEntity.ok(announcement);
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Lỗi công bố kết quả: " + e.getMessage());
    }
  }

  /**
   * Admin endpoint: Giải mã tất cả phiếu mã hóa của một election sau khi bầu cử kết thúc.
   * Gọi crypto-service /api/crypto/decrypt-vote cho mỗi phiếu có encryptedVote != null.
   * Cập nhật candidate.voteCount dựa trên kết quả giải mã.
   */
  @PostMapping("/decrypt-results/{electionId}")
  @Transactional
  public ResponseEntity<?> decryptResults(@PathVariable Long electionId) {
    try {
      Optional<Election> electionOpt = electionRepository.findById(electionId);
      if (electionOpt.isEmpty()) return ResponseEntity.badRequest().body("Không tìm thấy cuộc bầu cử.");

      Election election = electionOpt.get();
      if (!"CLOSED".equals(election.getStatus()) && !"ENDED".equals(election.getStatus())) {
        return ResponseEntity.badRequest().body("Chỉ giải mã sau khi cuộc bầu cử đóng.");
      }

      // Lấy round cuối cùng đã CLOSED để decrypt đúng vòng
      List<com.nlu.electionservice.entity.ElectionRound> rounds =
          electionRoundRepository.findByElectionId(electionId);
      com.nlu.electionservice.entity.ElectionRound lastRound = rounds.stream()
          .filter(r -> "CLOSED".equals(r.getStatus()))
          .max(java.util.Comparator.comparing(com.nlu.electionservice.entity.ElectionRound::getRoundNumber))
          .orElse(null);

      if (lastRound == null) return ResponseEntity.badRequest().body("Không tìm thấy vòng đã kết thúc.");

      // Chỉ decrypt phiếu của round cuối — tránh cộng dồn voteCount giữa các vòng
      List<com.nlu.electionservice.entity.Vote> encryptedVotes =
          voteRepository.findByElectionIdAndRoundIdAndEncryptedVoteIsNotNull(electionId, lastRound.getId());

      if (encryptedVotes.isEmpty()) return ResponseEntity.ok(Map.of("decrypted", 0, "message", "Không có phiếu mã hóa."));

      String cryptoUrl = cryptoServiceUrl + "/api/crypto/decrypt-vote";
      int decrypted = 0;
      int failed = 0;
      com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

      for (com.nlu.electionservice.entity.Vote vote : encryptedVotes) {
        try {
          ResponseEntity<Map> res = restTemplate.postForEntity(
              cryptoUrl, new HttpEntity<>(Map.of("encryptedVote", vote.getEncryptedVote()), internalHeaders()), Map.class);
          if (res.getBody() != null && res.getBody().containsKey("plaintext")) {
            String plaintext = (String) res.getBody().get("plaintext");
            Map<String, Object> parsed = om.readValue(plaintext, Map.class);
            Long candidateId = Long.valueOf(parsed.get("candidateId").toString());
            vote.setCandidateId(candidateId);
            voteRepository.save(vote);
            // Atomic increment — tránh race condition khi nhiều phiếu cùng ứng viên
            candidateRepository.incrementVoteCount(candidateId);
            decrypted++;
          }
        } catch (Exception ex) {
          failed++;
          log.error("[DecryptResults] Lỗi giải mã vote id={}: {}", vote.getId(), ex.getMessage());
        }
      }

      return ResponseEntity.ok(Map.of("decrypted", decrypted, "failed", failed, "total", encryptedVotes.size()));
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Lỗi giải mã kết quả: " + e.getMessage());
    }
  }
}

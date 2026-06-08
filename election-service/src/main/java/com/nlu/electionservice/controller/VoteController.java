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
import com.nlu.electionservice.repository.UserRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.service.ElectionService;
import com.nlu.electionservice.service.ElectionParticipantInviteService;
import com.nlu.electionservice.service.KafkaProducerService;
import com.nlu.electionservice.service.VoteService;
import java.util.HashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

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
  private KafkaProducerService auditLogger;

  @Autowired
  private ElectionParticipantInviteService participantInviteService;

  private final RestTemplate restTemplate = new RestTemplate();
  private final String CRYPTO_PUBLIC_KEY_URL = "http://localhost:8084/api/crypto/public-key";
  private final String CRYPTO_SIGN_URL = "http://localhost:8084/api/crypto/sign-e2e";

  @PostMapping("/cast-e2e")
  public ResponseEntity<?> castE2EVote(
      @RequestBody VoteE2ERequest request,
      @RequestHeader(value = "X-User-Email", required = false) String email) {
      try {
          // BÆ°á»›c 1: Kiá»ƒm tra xem ngÆ°á»i dÃ¹ng Ä‘Ã£ bá» phiáº¿u vÃ²ng nÃ y chÆ°a
          User user;
          String auditEmail = email;
          if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
              ElectionVoterInvite invite = participantInviteService.resolveVerifiedInvite(
                  request.getInviteToken(),
                  request.getElectionId(),
                  request.getRoundId());
              user = userRepository.findById(invite.getVoterId())
                  .orElseThrow(() -> new RuntimeException("Nguoi tham gia khong hop le."));
              auditEmail = invite.getEmail();
          } else {
              if (email == null || email.isBlank()) {
                  return ResponseEntity.status(401).body("Vui long dang nhap hoac xac thuc bang link moi.");
              }
              user = userRepository.findByEmail(email)
                  .orElseThrow(() -> new RuntimeException("Nguoi dung khong hop le."));
          }

          if (electionVoterRepository.countByElectionIdAndVoterId(request.getElectionId(), user.getId()) == 0) {
              return ResponseEntity.status(403).body("Ban khong nam trong danh sach nhan vien duoc tham gia cuoc bau cu nay.");
          }

          boolean hasVoted = blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(
              user.getId(),
              request.getElectionId(),
              request.getRoundId()
          );

          if (hasVoted) {
              return ResponseEntity.badRequest().body("Báº¡n Ä‘Ã£ bá» phiáº¿u cho vÃ²ng nÃ y rá»“i.");
          }

          // BÆ°á»›c 2: Gá»­i blind token Ä‘áº¿n crypto-service Ä‘á»ƒ kÃ½
          ResponseEntity<HashMap> signResponse = restTemplate.postForEntity(
              CRYPTO_SIGN_URL,
              Map.of("blindToken", request.getBlindToken()),
              HashMap.class
          );
          if (signResponse.getBody() == null || !signResponse.getBody().containsKey("signedBlindToken")) {
              return ResponseEntity.status(500).body("Lá»—i khi kÃ½ token.");
          }

          BigInteger signedBlindToken = new BigInteger(
              (String) signResponse.getBody().get("signedBlindToken"), 16);

          // BÆ°á»›c 3: Gá»i VoteService Ä‘á»ƒ xá»­ lÃ½ logic bá» phiáº¿u E2E
          String receiptCode = voteService.castE2EVote(request, signedBlindToken, user.getId());
          auditLogger.sendAuditEvent(auditEmail, "VOTE_CAST_SUCCESS",
              "User cast vote for election ID " + request.getElectionId() + ", round ID " + request.getRoundId());

          // Tráº£ vá» mÃ£ biÃªn nháº­n cho ngÆ°á»i dÃ¹ng
          return ResponseEntity.ok(Map.of(
              "message", "Bá» phiáº¿u thÃ nh cÃ´ng! LÃ¡ phiáº¿u cá»§a báº¡n Ä‘Ã£ Ä‘Æ°á»£c ghi láº¡i.",
              "receiptCode", receiptCode
          ));

      } catch (Exception e) {
          auditLogger.sendAuditEvent(email != null ? email : "invite-voter", "VOTE_CAST_FAILED", "Vote failed: " + e.getMessage());
          return ResponseEntity.badRequest().body("Lá»—i trong quÃ¡ trÃ¬nh bá» phiáº¿u: " + e.getMessage());
      }
  }

  @PostMapping("/submit-anonymous")
  public ResponseEntity<?> submitVote(@RequestBody VoteAnonymousRequest request) {
    try {
      System.out.println(">>> [BE Election] Nháº­n yÃªu cáº§u bá» phiáº¿u náº·c danh cho RoundID: " + request.getRoundId());
      
      ResponseEntity<Map<String, String>> response = restTemplate.exchange(
          CRYPTO_PUBLIC_KEY_URL,
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<Map<String, String>>() {}
      );
      Map<String, String> keyParams = response.getBody();

      if (keyParams == null || !keyParams.containsKey("modulus") || !keyParams.containsKey("exponent")) {
        throw new RuntimeException("KhÃ´ng thá»ƒ káº¿t ná»‘i láº¥y khÃ³a báº£o máº­t há»‡ thá»‘ng tá»« dá»‹ch vá»¥ mÃ£ hÃ³a!");
      }

      BigInteger N = new BigInteger(keyParams.get("modulus").trim(), 16);
      BigInteger E = new BigInteger(keyParams.get("exponent").trim(), 16);

      electionService.submitAnonymousVote(request, N, E);

      return ResponseEntity.ok(Map.of("message", "Bá» phiáº¿u thÃ nh cÃ´ng! Phiáº¿u Ä‘Ã£ Ä‘Æ°á»£c lÆ°u náº·c danh."));
    } catch (Exception e) {
      System.err.println(">>> [BE Election ERROR] Lá»—i xá»­ lÃ½ hÃ²m phiáº¿u: " + e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/results")
  public ResponseEntity<?> getElectionResults(
      @RequestParam("electionId") Long electionId,
      @RequestParam("roundId") Long roundId) {
    try {
      System.out.println(">>> [BE Election] Tá»•ng há»£p káº¿t quáº£ hÃ²m phiáº¿u cho ElectionID: " + electionId + ", RoundID: " + roundId);

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
      String roundTitleDisplay = "Báº§u cá»­ vÃ²ng " + roundId;

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
          cleanElectionInfo.put("title", "Cuá»™c báº§u cá»­ sá»‘ #" + electionId);
          cleanElectionInfo.put("status", "CLOSED");
        }
      } catch (Exception ex) {
        cleanElectionInfo.put("id", electionId);
        cleanElectionInfo.put("title", "Cuá»™c báº§u cá»­ sá»‘ #" + electionId);
        cleanElectionInfo.put("status", "CLOSED");
      }

      Map<String, Object> responsePayload = new java.util.HashMap<>();
      responsePayload.put("election", cleanElectionInfo);
      responsePayload.put("roundTitle", roundTitleDisplay);
      responsePayload.put("votes", voteStats);

      return ResponseEntity.ok(responsePayload);
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Lá»—i xá»­ lÃ½ káº¿t quáº£ kiá»ƒm phiáº¿u: " + e.getMessage());
    }
  }

  @GetMapping("/announcement/{electionId}")
  public ResponseEntity<?> getOfficialAnnouncement(@PathVariable Long electionId) {
    try {
      Optional<Election> electionOpt = electionRepository.findById(electionId);
      if (electionOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("KhÃ´ng tÃ¬m tháº¥y cuá»™c báº§u cá»­");
      }

      Election election = electionOpt.get();
      if (!("CLOSED".equals(election.getStatus()) || "ENDED".equals(election.getStatus()))) {
        return ResponseEntity.badRequest().body("Cuá»™c báº§u cá»­ chÆ°a káº¿t thÃºc");
      }

      Long winnerId = election.getWinnerId();
      if (winnerId == null) {
        return ResponseEntity.badRequest().body("Cuá»™c báº§u cá»­ chÆ°a cÃ³ káº¿t quáº£ chÃ­nh thá»©c");
      }

      var finalRoundOpt = roundRepository.findByElectionIdAndRoundNumber(electionId, election.getTotalRounds());
      if (finalRoundOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("KhÃ´ng tÃ¬m tháº¥y vÃ²ng cuá»‘i cÃ¹ng");
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
        if (cmp != 0) return cmp;
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
      return ResponseEntity.status(500).body("Lá»—i cÃ´ng bá»‘ káº¿t quáº£: " + e.getMessage());
    }
  }
}


package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.VoteAnonymousRequest;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.repository.AnonymousVoteRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.service.ElectionService;
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
@RequestMapping("/api/votes")
public class VoteController {

  @Autowired
  private ElectionService electionService;

  @Autowired
  private VoteRepository voteRepository;

  @Autowired
  private ElectionRoundRepository roundRepository;
  
  @Autowired
  private ElectionRepository electionRepository;

  @Autowired
  private AnonymousVoteRepository anonymousVoteRepository;

  private final RestTemplate restTemplate = new RestTemplate();
  private final String CRYPTO_PUBLIC_KEY_URL = "http://localhost:8084/api/crypto/public-key";

  @PostMapping("/submit-anonymous")
  public ResponseEntity<?> submitVote(@RequestBody VoteAnonymousRequest request) {
    try {
      System.out.println(">>> [BE Election] Nhận yêu cầu bỏ phiếu nặc danh cho RoundID: " + request.getRoundId());
      
      // Sửa lỗi unchecked operations
      ResponseEntity<Map<String, String>> response = restTemplate.exchange(
          CRYPTO_PUBLIC_KEY_URL,
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<Map<String, String>>() {}
      );
      Map<String, String> keyParams = response.getBody();

      if (keyParams == null || !keyParams.containsKey("modulus") || !keyParams.containsKey("exponent")) {
        throw new RuntimeException("Không thể kết nối lấy khóa bảo mật hệ thống từ dịch vụ mã hóa!");
      }

      BigInteger N = new BigInteger(keyParams.get("modulus").trim(), 16);
      BigInteger E = new BigInteger(keyParams.get("exponent").trim(), 16);

      electionService.submitAnonymousVote(request, N, E);

      return ResponseEntity.ok(Map.of("message", "Bỏ phiếu thành công! Phiếu đã được lưu nặc danh."));
    } catch (Exception e) {
      System.err.println(">>> [BE Election ERROR] Lỗi xử lý hòm phiếu: " + e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/results")
  public ResponseEntity<?> getElectionResults(
      @RequestParam("electionId") Long electionId,
      @RequestParam("roundId") Long roundId) {
    try {
      System.out.println(">>> [BE Election] Tổng hợp kết quả hòm phiếu cho ElectionID: " + electionId + ", RoundID: " + roundId);

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
        return ResponseEntity.badRequest().body("Không tìm thấy cuộc bầu cử");
      }

      Election election = electionOpt.get();
      if (!("CLOSED".equals(election.getStatus()) || "ENDED".equals(election.getStatus()))) {
        return ResponseEntity.badRequest().body("Cuộc bầu cử chưa kết thúc");
      }

      Long winnerId = election.getWinnerId();
      if (winnerId == null) {
        return ResponseEntity.badRequest().body("Cuộc bầu cử chưa có kết quả chính thức");
      }

      var finalRoundOpt = roundRepository.findByElectionIdAndRoundNumber(electionId, election.getTotalRounds());
      if (finalRoundOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("Không tìm thấy vòng cuối cùng");
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
      return ResponseEntity.status(500).body("Lỗi công bố kết quả: " + e.getMessage());
    }
  }
}
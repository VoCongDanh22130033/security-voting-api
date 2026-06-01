package com.nlu.electionservice.controller;

import com.nlu.electionservice.dto.VoteAnonymousRequest;
import com.nlu.electionservice.repository.AnonymousVoteRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.service.ElectionService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.math.BigInteger;
import java.util.Map;

@RestController
@RequestMapping("/api/votes")
public class VoteController {

  @Autowired
  private ElectionService electionService;

  @Autowired
  private VoteRepository voteRepository;

  @Autowired
  private AnonymousVoteRepository anonymousVoteRepository;

  private final RestTemplate restTemplate = new RestTemplate();
  private final String CRYPTO_PUBLIC_KEY_URL = "http://localhost:8084/api/crypto/public-key";

  @PostMapping("/submit-anonymous")
  public ResponseEntity<?> submitVote(@RequestBody VoteAnonymousRequest request) {
    try {
      System.out.println(">>> [BE Election] Đang gọi lấy khóa RSA công khai từ Crypto-Service...");
      Map<String, String> keyParams = restTemplate.getForObject(CRYPTO_PUBLIC_KEY_URL, Map.class);

      if (keyParams == null || !keyParams.containsKey("modulus") || !keyParams.containsKey("exponent")) {
        throw new RuntimeException("Không thể kết nối lấy khóa bảo mật hệ thống từ dịch vụ mã hóa!");
      }

      BigInteger N = new BigInteger(keyParams.get("modulus").trim(), 16);
      BigInteger E = new BigInteger(keyParams.get("exponent").trim(), 16);

      electionService.submitAnonymousVote(request, N, E);

      return ResponseEntity.ok(Map.of("message", "Bỏ phiếu thành công! Phiếu đã được lưu nặc danh."));
    } catch (Exception e) {
      System.err.println(">>> [BE Election ERROR] Lỗi xử lý hòm phiếu: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @GetMapping("/results")
  public ResponseEntity<?> getElectionResults(
      @RequestParam("electionId") Long electionId,
      @RequestParam("roundId") Long roundId) {
    try {
      System.out.println(">>> [BE Election] Đang tổng hợp kết quả từ bảng votes thực tế...");

      // ĐÃ SỬA: Gọi hàm Repo trống không tham số để kéo toàn bộ hòm phiếu lên an toàn
      List<Map<String, Object>> voteStats = anonymousVoteRepository.countVotesByCandidate();
      if (voteStats == null) voteStats = new java.util.ArrayList<>();

      // Lấy thông tin cuộc bầu cử và bóc tách sang Map phẳng để TRIỆT TIÊU HOÀN TOÀN ĐỆ QUY VÔ HẠN JSON
      Map<String, Object> cleanElectionInfo = new java.util.HashMap<>();
      try {
        Object rawElection = electionService.getElectionById(electionId);

        if (rawElection instanceof com.nlu.electionservice.entity.Election) {
          com.nlu.electionservice.entity.Election election = (com.nlu.electionservice.entity.Election) rawElection;
          cleanElectionInfo.put("id", election.getId());
          cleanElectionInfo.put("title", election.getTitle());
          cleanElectionInfo.put("description", election.getDescription());
          cleanElectionInfo.put("status", election.getStatus() != null ? election.getStatus().toString() : "ENDED");
        } else {
          cleanElectionInfo.put("id", electionId);
          cleanElectionInfo.put("title", "Cuộc bầu cử số #" + electionId);
          cleanElectionInfo.put("status", "ENDED");
        }
      } catch (Exception ex) {
        System.err.println(">>> [BE WARNING] Không thể bóc tách dữ liệu cuộc bầu cử ID: " + electionId + " - Lỗi: " + ex.getMessage());
        cleanElectionInfo.put("id", electionId);
        cleanElectionInfo.put("title", "Cuộc bầu cử số #" + electionId);
        cleanElectionInfo.put("status", "ENDED");
      }

      Map<String, Object> responsePayload = new java.util.HashMap<>();
      responsePayload.put("election", cleanElectionInfo);
      responsePayload.put("votes", voteStats);

      return ResponseEntity.ok(responsePayload);
    } catch (Exception e) {
      System.err.println(">>> [BE CRITICAL ERROR] Lỗi hệ thống khi kiểm phiếu: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.status(500).body("Lỗi xử lý kết quả kiểm phiếu: " + e.getMessage());
    }
  }
}
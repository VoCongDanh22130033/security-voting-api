package com.nlu.cryptoservice.controller;

import com.nlu.cryptoservice.entity.User;
import com.nlu.cryptoservice.entity.BlindSignatureLog;
import com.nlu.cryptoservice.dto.BlindRequest;
import com.nlu.cryptoservice.dto.SignatureResponse;
import com.nlu.cryptoservice.service.BlindSignatureService;
import com.nlu.cryptoservice.repository.BlindSignatureLogRepository;
import com.nlu.cryptoservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigInteger;
import java.util.Base64;

@RestController
@RequestMapping("/api/crypto")
public class CryptoController {

  @Autowired
  private BlindSignatureService blindSignatureService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private BlindSignatureLogRepository blindSignatureLogRepository;

  @PostMapping("/sign")
  public ResponseEntity<?> signMessage(
      @RequestBody BlindRequest request,
      @RequestHeader(value = "X-User-Email", required = false) String email) {

    try {
      // 1. Kiểm tra Email người dùng[cite: 13]
      if (email == null) return ResponseEntity.badRequest().body("Thiếu Header X-User-Email");

      User user = userRepository.findByEmail(email)
          .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + email));

      // 2. Giải mã Base64 an toàn để tránh lỗi 'Illegal character'[cite: 13]
      byte[] blindedBytes;
      try {
        blindedBytes = Base64.getDecoder().decode(request.getBlindedMessage());
      } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body("Dữ liệu mù không đúng định dạng Base64");
      }

      // 3. Thực hiện ký mù[cite: 13]
      BigInteger blindedMessage = new BigInteger(1, blindedBytes);
      BigInteger signature = blindSignatureService.signBlindedMessage(blindedMessage);

      // 4. LƯU LOG: Để bảng blind_signature_logs có dữ liệu[cite: 13]
      BlindSignatureLog log = new BlindSignatureLog();
      log.setUserId(user.getId());
      log.setElectionId(request.getElectionId());
      blindSignatureLogRepository.save(log);

      String encodedSig = Base64.getEncoder().encodeToString(signature.toByteArray());
      return ResponseEntity.ok(new SignatureResponse(encodedSig));

    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.internalServerError().body("Lỗi: " + e.getMessage());
    }
  }
}
package com.nlu.cryptoservice.controller;

import com.nlu.cryptoservice.entity.User;
import com.nlu.cryptoservice.entity.BlindSignatureLog;
import com.nlu.cryptoservice.dto.BlindRequest;
import com.nlu.cryptoservice.dto.SignatureResponse;
import com.nlu.cryptoservice.service.BlindSignatureService;
import com.nlu.cryptoservice.service.RSAService; // Cần import RSAService
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
  private RSAService rsaService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private BlindSignatureLogRepository blindSignatureLogRepository;

  // BỔ SUNG: Cung cấp khóa công khai (N, E) cho Frontend làm mù phiếu
  @GetMapping("/public-key")
  public ResponseEntity<?> getPublicKey() {
    return ResponseEntity.ok(rsaService.getPublicKeyParams());
  }

  @PostMapping("/sign")
  public ResponseEntity<?> signMessage(
      @RequestBody BlindRequest request,
      @RequestHeader(value = "X-User-Email", required = false) String email) {

    try {
      if (email == null) return ResponseEntity.badRequest().body("Thiếu Header X-User-Email");

      User user = userRepository.findByEmail(email)
          .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + email));

      // 1. Giải mã Base64 từ Frontend gửi lên[cite: 13]
      byte[] blindedBytes;
      try {
        blindedBytes = Base64.getDecoder().decode(request.getBlindedMessage());
      } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body("Dữ liệu mù không đúng định dạng Base64");
      }

      // 2. Thực hiện ký mù bằng Private Key[cite: 13]
      BigInteger blindedMessage = new BigInteger(1, blindedBytes);
      BigInteger signature = blindSignatureService.signBlindedMessage(blindedMessage);

      // 3. LƯU LOG vào bảng blind_signature_logs[cite: 13]
      BlindSignatureLog log = new BlindSignatureLog();
      log.setUserId(user.getId());
      log.setElectionId(request.getElectionId());
      blindSignatureLogRepository.save(log);

      // 4. Trả về chữ ký dạng Hex để Frontend dễ dàng thực hiện phép toán giải mù[cite: 13]
      return ResponseEntity.ok(new SignatureResponse(signature.toString(16)));

    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.internalServerError().body("Lỗi: " + e.getMessage());
    }
  }
}
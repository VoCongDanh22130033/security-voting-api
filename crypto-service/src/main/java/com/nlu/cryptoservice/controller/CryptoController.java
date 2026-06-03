package com.nlu.cryptoservice.controller;

import com.nlu.cryptoservice.entity.User;
import com.nlu.cryptoservice.entity.BlindSignatureLog;
import com.nlu.cryptoservice.dto.BlindRequest;
import com.nlu.cryptoservice.dto.SignatureResponse;
import com.nlu.cryptoservice.service.BlindSignatureService;
import com.nlu.cryptoservice.service.RSAService;
import com.nlu.cryptoservice.repository.BlindSignatureLogRepository;
import com.nlu.cryptoservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigInteger;
import java.util.Map;

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

  @GetMapping("/public-key")
  public ResponseEntity<?> getPublicKey() {
    return ResponseEntity.ok(rsaService.getPublicKeyParams());
  }

  @PostMapping("/sign")
  public ResponseEntity<?> signMessage(
      @RequestBody BlindRequest request,
      @RequestHeader(value = "X-User-Email", required = false) String email) {

    System.out.println("\n========== [BACKEND TRACE LOG] NHẬN REQUEST XIN KÝ MÙ ==========");
    System.out.println("  > Header X-User-Email: " + email);

    if (request == null) {
      System.out.println("  > LỖI: Request Body nhận được bị rỗng (null)!");
      return ResponseEntity.badRequest().body("Gói tin Request Body gửi lên trống rỗng!");
    }

    System.out.println("  > Dữ liệu map vào BlindRequest DTO:");
    System.out.println("    - electionId: " + request.getElectionId());
    System.out.println("    - roundId: " + request.getRoundId());
    System.out.println("    - blindedMessage (Chuỗi Hex): " + (request.getBlindedMessage() != null ? request.getBlindedMessage().substring(0, Math.min(request.getBlindedMessage().length(), 20)) + "..." : "null"));

    try {
      if (email == null || email.trim().isEmpty()) {
        return ResponseEntity.badRequest().body("Thiếu thông tin xác thực tài khoản cử tri (X-User-Email)!");
      }

      if (request.getElectionId() == null) {
        return ResponseEntity.badRequest().body("Hệ thống yêu cầu trường thông tin 'electionId'!");
      }
      if (request.getRoundId() == null) {
        return ResponseEntity.badRequest().body("Thiếu thông tin vòng bầu cử 'roundId'!");
      }

      User user = userRepository.findByEmail(email)
          .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng trên hệ thống: " + email));
      System.out.println("  > Tìm thấy User hợp lệ trong Database. ID: " + user.getId());

      boolean alreadySigned = blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(
          user.getId(),
          request.getElectionId(),
          request.getRoundId()
      );

      if (alreadySigned) {
        return ResponseEntity.badRequest().body("Bạn Đã Bầu Cử Vòng Này RỒi !!!");
      }

      String blindedMsgStr = request.getBlindedMessage();
      if (blindedMsgStr == null || blindedMsgStr.trim().isEmpty()) {
        return ResponseEntity.badRequest().body("Dữ liệu thông điệp mù không được rỗng!");
      }

      // Đọc toán học hệ Hex cơ số 16
      BigInteger blindedMessage = new BigInteger(blindedMsgStr.trim(), 16);
      System.out.println("  > Khởi tạo BigInteger từ chuỗi Hex thành công.");

      BigInteger signature = blindSignatureService.signBlindedMessage(blindedMessage);
      System.out.println("  > Ký số RSA thành công. Chữ ký xuất xưởng (Hex): " + signature.toString(16).substring(0, 15) + "...");

      // Lưu vết log nạp phiếu kèm theo đầy đủ 3 trường thông tin định danh đa nhiệm
      BlindSignatureLog log = new BlindSignatureLog();
      log.setUserId(user.getId());
      log.setElectionId(request.getElectionId());
      log.setRoundId(request.getRoundId());

      try {
        // ĐÃ SỬA: Bọc cố định lệnh lưu dữ liệu để bắt lỗi trùng lặp ràng buộc duy nhất (Unique Constraint)[cite: 6]
        blindSignatureLogRepository.save(log);
        System.out.println("  > Ghi Log vào Database bảng blind_signature_logs thành công!");
      } catch (org.springframework.dao.DataIntegrityViolationException dive) {
        System.err.println("  > CHẶN GIAN LẬN: Phát hiện xung đột trùng lặp bản ghi do gửi đồng thời!");
        return ResponseEntity.badRequest().body("Yêu cầu bị từ chối! Bạn đã nhận mã token chữ ký cho vòng này rồi.");
      }

      System.out.println("==================== HẾT LOG TRACE (XỬ LÝ OK) ====================\n");
      return ResponseEntity.ok(new SignatureResponse(signature.toString(16)));

    } catch (NumberFormatException e) {
      System.err.println("  > CRASH: Lỗi định dạng Radix 16 chuỗi blindedMessage không hợp lệ!");
      return ResponseEntity.badRequest().body("Lỗi định dạng toán học: Chuỗi blindedMessage không phải hệ Hex hợp lệ!");
    } catch (Exception e) {
      System.err.println("  > CRASH: Lỗi hệ thống phát sinh: " + e.getMessage());
      e.printStackTrace();
      return ResponseEntity.internalServerError().body("Lỗi hệ thống phát sinh: " + e.getMessage());
    }
  }
}
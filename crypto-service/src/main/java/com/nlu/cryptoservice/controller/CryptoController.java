package com.nlu.cryptoservice.controller;

import com.nlu.cryptoservice.entity.User;
import com.nlu.cryptoservice.entity.BlindSignatureLog;
import com.nlu.cryptoservice.dto.BlindRequest;
import com.nlu.cryptoservice.dto.SignatureResponse;
import com.nlu.cryptoservice.service.BlindSignatureService;
import com.nlu.cryptoservice.service.RSAService;
import com.nlu.cryptoservice.repository.BlindSignatureLogRepository;
import com.nlu.cryptoservice.repository.UserRepository;
import org.bouncycastle.crypto.encodings.OAEPEncoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;

@Slf4j
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

  @Value("${app.election-service-url:http://localhost:8083}")
  private String electionServiceUrl;

  @Value("${app.internal-service-token}")
  private String internalToken;

  private final RestTemplate restTemplate = new RestTemplate();

  private HttpEntity<Object> internalEntity(Object body) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.set("X-Internal-Token", internalToken);
    return new HttpEntity<>(body, h);
  }

  /** Lấy public key. Nếu có electionId thì trả key của election đó, không thì trả system key. */
  @GetMapping("/public-key")
  public ResponseEntity<?> getPublicKey(
      @RequestParam(value = "electionId", required = false) Long electionId) {
    return ResponseEntity.ok(electionId != null
        ? rsaService.getPublicKeyParams(electionId)
        : rsaService.getPublicKeyParams());
  }

  /**
   * Sinh cặp khóa RSA-2048 cho election mới (gọi ngay sau khi tạo election).
   * Nếu election đã có key thì trả về key hiện có (idempotent).
   */
  @PostMapping("/election-keys/generate")
  public ResponseEntity<?> generateElectionKey(@RequestBody Map<String, Long> payload) {
    Long electionId = payload.get("electionId");
    if (electionId == null) return ResponseEntity.badRequest().body("electionId không được rỗng");
    rsaService.getOrGenerateKeyPairForElection(electionId);
    Map<String, String> pub = rsaService.getPublicKeyParams(electionId);
    log.info(">>> [Crypto] Đã sinh/load key pair cho election {}.", electionId);
    return ResponseEntity.ok(Map.of("electionId", electionId, "modulus", pub.get("modulus")));
  }

  /**
   * Trả về RSA public key định dạng JWK (Base64URL) để Web Crypto API dùng mã hóa RSA-OAEP.
   * Client dùng key này để mã hóa nội dung phiếu bầu — server không biết candidateId khi nhận vote.
   */
  @GetMapping("/vote-encryption-key")
  public ResponseEntity<?> getVoteEncryptionKey(
      @RequestParam(value = "electionId", required = false) Long electionId) {
    RSAKeyParameters pub = electionId != null
        ? rsaService.getPublicKey(electionId)
        : rsaService.getPublicKey();
    // Dùng toByteArrayUnsigned() để loại bỏ leading zero byte mà BigInteger.toByteArray() thêm vào
    byte[] nBytes = toUnsignedBytes(pub.getModulus());
    byte[] eBytes = toUnsignedBytes(pub.getExponent());
    return ResponseEntity.ok(Map.of(
        "kty", "RSA",
        "alg", "RSA-OAEP-256",
        "n",   Base64.getUrlEncoder().withoutPadding().encodeToString(nBytes),
        "e",   Base64.getUrlEncoder().withoutPadding().encodeToString(eBytes),
        "ext", true
    ));
  }

  /**
   * Giải mã phiếu bầu sau khi bầu cử kết thúc.
   * Input:  encryptedVote (hex string — RSA-OAEP ciphertext)
   * Output: plaintext JSON {"candidateId": X, "nonce": "..."}
   *
   * Chỉ dùng sau khi election CLOSED để đếm phiếu.
   */
  @PostMapping("/decrypt-vote")
  public ResponseEntity<?> decryptVote(@RequestBody Map<String, String> payload) {
    try {
      String encryptedHex = payload.get("encryptedVote");
      if (encryptedHex == null || encryptedHex.isBlank())
        return ResponseEntity.badRequest().body("encryptedVote không được rỗng");

      byte[] encrypted = hexToBytes(encryptedHex);

      Long electionId = null;
      if (payload.containsKey("electionId") && payload.get("electionId") != null) {
        try { electionId = Long.parseLong(payload.get("electionId")); } catch (NumberFormatException ignored) {}
      }

      // RSA-OAEP với SHA-256 (tương thích Web Crypto API RSA-OAEP-SHA256)
      OAEPEncoding cipher = new OAEPEncoding(
          new RSAEngine(), new SHA256Digest(), new SHA256Digest(), null);
      cipher.init(false, electionId != null ? rsaService.getPrivateKey(electionId) : rsaService.getPrivateKey());
      byte[] decrypted = cipher.processBlock(encrypted, 0, encrypted.length);

      return ResponseEntity.ok(Map.of("plaintext", new String(decrypted, java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Giải mã thất bại: " + e.getMessage());
    }
  }

  private static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2)
      data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                           + Character.digit(hex.charAt(i + 1), 16));
    return data;
  }

  @PostMapping("/sign-e2e")
  public ResponseEntity<?> signE2EMessage(@RequestBody Map<String, String> payload) {
      try {
          String blindedMsgStr = payload.get("blindToken");
          if (blindedMsgStr == null || blindedMsgStr.trim().isEmpty()) {
              return ResponseEntity.badRequest().body("blindToken không được rỗng!");
          }
          Long electionIdE2E = null;
          try { if (payload.get("electionId") != null) electionIdE2E = Long.parseLong(payload.get("electionId")); }
          catch (NumberFormatException ignored) {}
          BigInteger blindedMessage = new BigInteger(blindedMsgStr, 16);
          BigInteger signature = blindSignatureService.signBlindedMessage(blindedMessage, electionIdE2E);
          return ResponseEntity.ok(Map.of("signedBlindToken", signature.toString(16)));
      } catch (Exception e) {
          return ResponseEntity.internalServerError().body("Lỗi khi ký token: " + e.getMessage());
      }
  }

  @PostMapping("/sign")
  public ResponseEntity<?> signMessage(
      @RequestBody BlindRequest request,
      @RequestHeader(value = "X-User-Email", required = false) String email) {

    log.info("\n========== [BACKEND TRACE LOG] NHẬN REQUEST XIN KÝ MÙ ==========");
    log.info("  > Header X-User-Email: {}", email);

    if (request == null) {
      return ResponseEntity.badRequest().body("Gói tin Request Body gửi lên trống rỗng!");
    }

    try {
      if (request.getElectionId() == null) {
        return ResponseEntity.badRequest().body("Hệ thống yêu cầu trường thông tin 'electionId'!");
      }
      if (request.getRoundId() == null) {
        return ResponseEntity.badRequest().body("Thiếu thông tin vòng bầu cử 'roundId'!");
      }

      Long resolvedUserId = null;

      // Ưu tiên sử dụng inviteToken nếu có (luồng bầu cử nặc danh qua link)
      if (request.getInviteToken() != null && !request.getInviteToken().isEmpty()) {
          try {
              String verifyUrl = electionServiceUrl + "/api/elections/invites/verify-internal";
              Map<String, Object> reqPayload = Map.of(
                  "token", request.getInviteToken(),
                  "electionId", request.getElectionId(),
                  "roundId", request.getRoundId()
              );
              ResponseEntity<Map> response = restTemplate.postForEntity(verifyUrl, internalEntity(reqPayload), Map.class);
              if (response.getBody() != null && response.getBody().containsKey("voterId")) {
                  resolvedUserId = Long.valueOf(response.getBody().get("voterId").toString());
                  log.info("  > Xác thực bằng InviteToken thành công. UserId: {}", resolvedUserId);
              }
          } catch (Exception e) {
              return ResponseEntity.badRequest().body("Mã mời không hợp lệ hoặc đã hết hạn.");
          }
      } 
      // Nếu không có inviteToken, dùng email từ header (luồng đăng nhập)
      else if (email != null && !email.trim().isEmpty()) {
          User user = userRepository.findByEmail(email)
              .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng trên hệ thống: " + email));
          resolvedUserId = user.getId();
          log.info("  > Xác thực bằng Header Email thành công. UserId: {}", resolvedUserId);
      } else {
          return ResponseEntity.badRequest().body("Thiếu thông tin xác thực (Không có Invite Token hoặc X-User-Email)!");
      }

      if (resolvedUserId == null) {
          return ResponseEntity.badRequest().body("Không thể xác định danh tính cử tri.");
      }

      boolean alreadySigned = blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(
          resolvedUserId,
          request.getElectionId(),
          request.getRoundId()
      );

      if (alreadySigned) {
        return ResponseEntity.badRequest().body("Bạn đã bỏ phiếu vòng này rồi!");
      }

      String blindedMsgStr = request.getBlindedMessage();
      if (blindedMsgStr == null || blindedMsgStr.trim().isEmpty()) {
        return ResponseEntity.badRequest().body("Dữ liệu thông điệp mù không được rỗng!");
      }

      BigInteger blindedMessage = new BigInteger(blindedMsgStr.trim(), 16);
      BigInteger signature = blindSignatureService.signBlindedMessage(blindedMessage, request.getElectionId());

      BlindSignatureLog signatureLog = new BlindSignatureLog();
      signatureLog.setUserId(resolvedUserId);
      signatureLog.setElectionId(request.getElectionId());
      signatureLog.setRoundId(request.getRoundId());

      try {
        blindSignatureLogRepository.save(signatureLog);
      } catch (org.springframework.dao.DataIntegrityViolationException dive) {
        log.error("  > CHẶN GIAN LẬN: Phát hiện xung đột trùng lặp bản ghi do gửi đồng thời!");
        return ResponseEntity.badRequest().body("Yêu cầu bị từ chối! Bạn đã nhận mã token chữ ký cho vòng này rồi.");
      }

      log.info("==================== HẾT LOG TRACE (XỬ LÝ OK) ====================");
      return ResponseEntity.ok(new SignatureResponse(signature.toString(16)));

    } catch (NumberFormatException e) {
      return ResponseEntity.badRequest().body("Lỗi định dạng toán học: Chuỗi blindedMessage không hợp lệ!");
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("Lỗi hệ thống: " + e.getMessage());
    }
  }

  // Chuyển BigInteger thành byte[] không có leading zero (chuẩn JWK)
  private static byte[] toUnsignedBytes(java.math.BigInteger n) {
    byte[] bytes = n.toByteArray();
    if (bytes[0] == 0 && bytes.length > 1) {
      byte[] trimmed = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
      return trimmed;
    }
    return bytes;
  }
}

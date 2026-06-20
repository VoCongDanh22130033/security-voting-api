package com.nlu.cryptoservice.service;

import com.nlu.cryptoservice.entity.CryptoKey;
import com.nlu.cryptoservice.repository.CryptoKeyRepository;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RSAService {

  /** System-wide key pair (electionId = NULL). Used as fallback. */
  private AsymmetricCipherKeyPair systemKeyPair;

  /** Per-election key pairs loaded into memory. */
  private final ConcurrentHashMap<Long, AsymmetricCipherKeyPair> electionKeyPairs = new ConcurrentHashMap<>();

  @Autowired
  private CryptoKeyRepository cryptoKeyRepository;

  @PostConstruct
  public void init() {
    try {
      java.util.Optional<CryptoKey> existing = cryptoKeyRepository.findFirstByElectionIdIsNull();
      if (existing.isPresent()) {
        log.info(">>> [Crypto System] Phát hiện khóa RSA cũ trong DB. Tiến hành khôi phục lên bộ nhớ RAM...");
        systemKeyPair = loadKeyPairFromEntity(existing.get());
        log.info(">>> [Crypto System SUCCESS] Đã đồng bộ cặp khóa RSA từ DB lên hệ thống ổn định.");
      } else {
        log.info(">>> [Crypto System] DB trống. Đang tiến hành khởi tạo cặp khóa RSA nguyên bản...");
        systemKeyPair = generateAndSave(null);
        log.info(">>> [Crypto System SUCCESS] Đã sinh và lưu cặp khóa RSA vào DB thành công.");
      }
    } catch (Exception e) {
      log.error(">>> [Crypto System ERROR] Thất bại khi thiết lập trạng thái khóa: {}", e.getMessage(), e);
    }
  }

  /** Sinh và lưu cặp khóa RSA-2048 cho một election cụ thể. Nếu đã tồn tại thì trả về key cũ. */
  public synchronized AsymmetricCipherKeyPair getOrGenerateKeyPairForElection(Long electionId) {
    if (electionId == null) return systemKeyPair;

    // 1. Kiểm tra trong bộ nhớ RAM
    if (electionKeyPairs.containsKey(electionId)) {
      return electionKeyPairs.get(electionId);
    }

    // 2. Kiểm tra trong DB
    java.util.Optional<CryptoKey> dbKey = cryptoKeyRepository.findByElectionId(electionId);
    if (dbKey.isPresent()) {
      AsymmetricCipherKeyPair kp = loadKeyPairFromEntity(dbKey.get());
      electionKeyPairs.put(electionId, kp);
      log.info(">>> [Crypto] Đã load key pair cho election {} từ DB vào RAM.", electionId);
      return kp;
    }

    // 3. Sinh mới
    log.info(">>> [Crypto] Sinh cặp khóa RSA-2048 mới cho election {}...", electionId);
    AsymmetricCipherKeyPair kp = generateAndSave(electionId);
    electionKeyPairs.put(electionId, kp);
    log.info(">>> [Crypto] Đã sinh và lưu key pair cho election {}.", electionId);
    return kp;
  }

  /**
   * Lấy public key cho election. Nếu không có per-election key thì fallback về system key.
   * Fallback đảm bảo tương thích với dữ liệu cũ (elections tạo trước khi có per-election key).
   */
  public RSAKeyParameters getPublicKey(Long electionId) {
    AsymmetricCipherKeyPair kp = resolveKeyPair(electionId);
    return (RSAKeyParameters) kp.getPublic();
  }

  public RSAKeyParameters getPrivateKey(Long electionId) {
    AsymmetricCipherKeyPair kp = resolveKeyPair(electionId);
    return (RSAKeyParameters) kp.getPrivate();
  }

  /** Lấy system key (backward compat). */
  public RSAKeyParameters getPublicKey() { return (RSAKeyParameters) systemKeyPair.getPublic(); }
  public RSAKeyParameters getPrivateKey() { return (RSAKeyParameters) systemKeyPair.getPrivate(); }

  public Map<String, String> getPublicKeyParams() {
    RSAKeyParameters pub = getPublicKey();
    return Map.of("modulus", pub.getModulus().toString(16), "exponent", pub.getExponent().toString(16));
  }

  public Map<String, String> getPublicKeyParams(Long electionId) {
    RSAKeyParameters pub = getPublicKey(electionId);
    return Map.of("modulus", pub.getModulus().toString(16), "exponent", pub.getExponent().toString(16));
  }

  // ── private helpers ───────────────────────────────────────────────────────

  private AsymmetricCipherKeyPair resolveKeyPair(Long electionId) {
    if (electionId == null) return systemKeyPair;

    // Thử load per-election key
    if (electionKeyPairs.containsKey(electionId)) return electionKeyPairs.get(electionId);
    java.util.Optional<CryptoKey> dbKey = cryptoKeyRepository.findByElectionId(electionId);
    if (dbKey.isPresent()) {
      AsymmetricCipherKeyPair kp = loadKeyPairFromEntity(dbKey.get());
      electionKeyPairs.put(electionId, kp);
      return kp;
    }

    // Fallback: dùng system key cho các election cũ chưa có key riêng
    log.warn(">>> [Crypto] Không tìm thấy key cho election {}. Fallback về system key.", electionId);
    return systemKeyPair;
  }

  private AsymmetricCipherKeyPair generateAndSave(Long electionId) {
    RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
    generator.init(new RSAKeyGenerationParameters(new BigInteger("65537"), new SecureRandom(), 2048, 80));
    AsymmetricCipherKeyPair kp = generator.generateKeyPair();

    RSAKeyParameters pub  = (RSAKeyParameters) kp.getPublic();
    RSAKeyParameters priv = (RSAKeyParameters) kp.getPrivate();

    CryptoKey entity = new CryptoKey();
    entity.setElectionId(electionId);
    entity.setPublicKey(pub.getModulus().toString(16));
    entity.setPrivateKey(priv.getExponent().toString(16));
    cryptoKeyRepository.save(entity);
    return kp;
  }

  private AsymmetricCipherKeyPair loadKeyPairFromEntity(CryptoKey entity) {
    BigInteger modulus          = new BigInteger(entity.getPublicKey().trim(), 16);
    BigInteger privateExponent  = new BigInteger(entity.getPrivateKey().trim(), 16);
    BigInteger publicExponent   = new BigInteger("65537");
    return new AsymmetricCipherKeyPair(
        new RSAKeyParameters(false, modulus, publicExponent),
        new RSAKeyParameters(true,  modulus, privateExponent));
  }
}

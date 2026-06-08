package com.nlu.cryptoservice.service;

import com.nlu.cryptoservice.entity.CryptoKey;
import com.nlu.cryptoservice.repository.CryptoKeyRepository;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Map;

@Service
public class RSAService {
  private AsymmetricCipherKeyPair keyPair;

  @Autowired
  private CryptoKeyRepository cryptoKeyRepository;

  private final Long DEFAULT_SYSTEM_ID = 1L;

  @PostConstruct
  public void init() {
    try {
      if (cryptoKeyRepository.existsById(DEFAULT_SYSTEM_ID)) {
        System.out.println(">>> [Crypto System] Phát hiện khóa RSA cũ trong DB. Tiến hành khôi phục lên bộ nhớ RAM...");

        CryptoKey keyEntity = cryptoKeyRepository.findById(DEFAULT_SYSTEM_ID).get();
        BigInteger modulus = new BigInteger(keyEntity.getPublicKey().trim(), 16);
        BigInteger privateExponent = new BigInteger(keyEntity.getPrivateKey().trim(), 16);
        BigInteger publicExponent = new BigInteger("65537"); // Số mũ e chuẩn cố định

        RSAKeyParameters pubKeyParams = new RSAKeyParameters(false, modulus, publicExponent);
        RSAKeyParameters privKeyParams = new RSAKeyParameters(true, modulus, privateExponent);

        this.keyPair = new AsymmetricCipherKeyPair(pubKeyParams, privKeyParams);
        System.out.println(">>> [Crypto System SUCCESS] Đã đồng bộ cặp khóa RSA từ DB lên hệ thống ổn định.");
      } else {
        // Nếu DB trống rỗng hoàn toàn thì mới tiến hành sinh lần đầu tiên
        System.out.println(">>> [Crypto System] DB trống. Đang tiến hành khởi tạo cặp khóa RSA nguyên bản...");
        org.bouncycastle.crypto.generators.RSAKeyPairGenerator generator = new org.bouncycastle.crypto.generators.RSAKeyPairGenerator();
        generator.init(new org.bouncycastle.crypto.params.RSAKeyGenerationParameters(
            new BigInteger("65537"), new java.security.SecureRandom(), 2048, 80));
        this.keyPair = generator.generateKeyPair();

        RSAKeyParameters pub = (RSAKeyParameters) keyPair.getPublic();
        RSAKeyParameters priv = (RSAKeyParameters) keyPair.getPrivate();

        CryptoKey keyEntity = new CryptoKey();
        keyEntity.setElectionId(DEFAULT_SYSTEM_ID); // Gán ID cố định để tránh lỗi Identifier
        keyEntity.setPublicKey(pub.getModulus().toString(16));
        keyEntity.setPrivateKey(priv.getExponent().toString(16));
        cryptoKeyRepository.save(keyEntity);
        System.out.println(">>> [Crypto System] Đã lưu cặp khóa RSA khởi thủy xuống cơ sở dữ liệu.");
      }
    } catch (Exception e) {
      System.err.println(">>> [Crypto System ERROR] Thất bại khi thiết lập trạng thái khóa: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public Map<String, String> getPublicKeyParams() {
    RSAKeyParameters pub = (RSAKeyParameters) keyPair.getPublic();
    return Map.of(
        "modulus", pub.getModulus().toString(16),
        "exponent", pub.getExponent().toString(16)
    );
  }

  public RSAKeyParameters getPublicKey() { return (RSAKeyParameters) keyPair.getPublic(); }
  public RSAKeyParameters getPrivateKey() { return (RSAKeyParameters) keyPair.getPrivate(); }
}
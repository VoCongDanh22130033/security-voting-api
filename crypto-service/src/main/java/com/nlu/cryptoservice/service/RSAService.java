package com.nlu.cryptoservice.service;

import com.nlu.cryptoservice.entity.CryptoKey;
import com.nlu.cryptoservice.repository.CryptoKeyRepository;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;

@Service
public class RSAService {
  private AsymmetricCipherKeyPair keyPair;

  @Autowired
  private CryptoKeyRepository cryptoKeyRepository;

  @PostConstruct
  public void init() {
    // 1. Sinh cặp khóa RSA 2048-bit
    RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
    generator.init(new RSAKeyGenerationParameters(new BigInteger("65537"), new SecureRandom(), 2048, 80));
    this.keyPair = generator.generateKeyPair();

    RSAKeyParameters pub = (RSAKeyParameters) keyPair.getPublic();
    RSAKeyParameters priv = (RSAKeyParameters) keyPair.getPrivate();

    // 2. Lưu khóa vào DB dưới dạng Hex để dễ dàng truy vấn hoặc debug[cite: 11]
    CryptoKey keyEntity = new CryptoKey();
    keyEntity.setPublicKey(pub.getModulus().toString(16));
    keyEntity.setPrivateKey(priv.getExponent().toString(16));
    cryptoKeyRepository.save(keyEntity);
    System.out.println(">>> [Crypto] Khởi tạo và lưu khóa RSA thành công.");
  }

  // 3. Trả về thông số cho Frontend làm mù phiếu[cite: 11, 14]
  public Map<String, String> getPublicKeyParams() {
    RSAKeyParameters pub = (RSAKeyParameters) keyPair.getPublic();
    return Map.of(
        "modulus", pub.getModulus().toString(16),
        "exponent", pub.getExponent().toString(16) // Sử dụng getExponent() cho RSAKeyParameters[cite: 11]
    );
  }

  // Các phương thức getter hỗ trợ cho các service nội bộ[cite: 11]
  public RSAKeyParameters getPublicKey() { return (RSAKeyParameters) keyPair.getPublic(); }
  public RSAKeyParameters getPrivateKey() { return (RSAKeyParameters) keyPair.getPrivate(); }
}
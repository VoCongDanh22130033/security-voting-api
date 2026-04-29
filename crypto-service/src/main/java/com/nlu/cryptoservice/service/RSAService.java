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
import java.util.Base64;

@Service
public class RSAService {
  private AsymmetricCipherKeyPair keyPair;

  @Autowired
  private CryptoKeyRepository cryptoKeyRepository;

  @PostConstruct
  public void init() {
    // Sinh cặp khóa RSA 2048-bit[cite: 12]
    RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
    generator.init(new RSAKeyGenerationParameters(new BigInteger("65537"), new SecureRandom(), 2048, 80));
    this.keyPair = generator.generateKeyPair();

    // Lấy thông số khóa[cite: 12]
    RSAKeyParameters pub = (RSAKeyParameters) keyPair.getPublic();
    RSAKeyParameters priv = (RSAKeyParameters) keyPair.getPrivate();

    // LƯU VÀO CSDL: Để bảng crypto_keys không còn trống[cite: 12]
    CryptoKey keyEntity = new CryptoKey();
    keyEntity.setPublicKey(Base64.getEncoder().encodeToString(pub.getModulus().toByteArray()));
    keyEntity.setPrivateKey(Base64.getEncoder().encodeToString(priv.getExponent().toByteArray()));

    System.out.println(">>> [Crypto] Đang lưu khóa RSA mới vào Database...");
    cryptoKeyRepository.save(keyEntity);
  }

  public RSAKeyParameters getPublicKey() { return (RSAKeyParameters) keyPair.getPublic(); }
  public RSAKeyParameters getPrivateKey() { return (RSAKeyParameters) keyPair.getPrivate(); }
}
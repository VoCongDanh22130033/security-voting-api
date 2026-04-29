package com.nlu.cryptoservice.service;


import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigInteger;

// BlindSignatureService.java
@Service
public class BlindSignatureService {

  @Autowired
  private RSAService rsaService;

  public BigInteger signBlindedMessage(BigInteger blindedMessage) {
    RSAKeyParameters privateKey = rsaService.getPrivateKey();
    // Server ký lên thông điệp đã mù: s' = (m')^d mod n[cite: 14]
    return blindedMessage.modPow(privateKey.getExponent(), privateKey.getModulus());
  }

  // Bổ sung: Xác thực chữ ký sau khi cử tri đã giải mù và gửi phiếu ẩn danh
  public boolean verifySignature(BigInteger message, BigInteger signature) {
    RSAKeyParameters publicKey = rsaService.getPublicKey();
    // Kiểm tra: signature^e mod n == message
    BigInteger result = signature.modPow(publicKey.getExponent(), publicKey.getModulus());
    return result.equals(message);
  }
}
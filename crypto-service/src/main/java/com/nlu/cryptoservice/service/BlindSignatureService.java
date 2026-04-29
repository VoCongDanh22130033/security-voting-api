package com.nlu.cryptoservice.service;

import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigInteger;

@Service
public class BlindSignatureService {

  @Autowired
  private RSAService rsaService;
  public BigInteger signBlindedMessage(BigInteger blindedMessage) {
    RSAKeyParameters privateKey = rsaService.getPrivateKey();
    return blindedMessage.modPow(privateKey.getExponent(), privateKey.getModulus());
  }
  public boolean verifySignature(BigInteger message, BigInteger signature) {
    RSAKeyParameters publicKey = rsaService.getPublicKey();
    BigInteger result = signature.modPow(publicKey.getExponent(), publicKey.getModulus());
    return result.equals(message);
  }
}
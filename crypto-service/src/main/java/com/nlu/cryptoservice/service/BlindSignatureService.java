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

  public BigInteger signBlindedMessage(BigInteger blindedMessage, Long electionId) {
    RSAKeyParameters privateKey = electionId != null
        ? rsaService.getPrivateKey(electionId)
        : rsaService.getPrivateKey();
    return blindedMessage.modPow(privateKey.getExponent(), privateKey.getModulus());
  }

  public boolean verifySignature(BigInteger message, BigInteger signature, Long electionId) {
    RSAKeyParameters publicKey = electionId != null
        ? rsaService.getPublicKey(electionId)
        : rsaService.getPublicKey();
    return signature.modPow(publicKey.getExponent(), publicKey.getModulus()).equals(message);
  }

  public boolean verifySignature(BigInteger message, BigInteger signature) {
    return verifySignature(message, signature, null);
  }
}

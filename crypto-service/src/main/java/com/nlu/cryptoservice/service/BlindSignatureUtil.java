package com.nlu.cryptoservice.service;

import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import java.math.BigInteger;

public class BlindSignatureUtil {
  public static BigInteger blind(BigInteger message, RSABlindingParameters params) {
    RSABlindingEngine engine = new RSABlindingEngine();
    engine.init(true, params);
    byte[] messageBytes = message.toByteArray();
    byte[] blindedBytes = engine.processBlock(messageBytes, 0, messageBytes.length);
    return new BigInteger(1, blindedBytes);
  }

  public static BigInteger unblind(BigInteger blindedSig, RSABlindingParameters params) {
    RSABlindingEngine engine = new RSABlindingEngine();
    engine.init(false, params);
    byte[] sigBytes = blindedSig.toByteArray();
    byte[] unblindedBytes = engine.processBlock(sigBytes, 0, sigBytes.length);
    return new BigInteger(1, unblindedBytes);
  }
}
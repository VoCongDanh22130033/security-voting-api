package com.nlu.electionservice.service;

import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

@Service
public class HomomorphicEncryptionService {

    private static final SecureRandom random = new SecureRandom();

    // Lớp nội bộ để lưu cặp khóa ElGamal
    public static class ElGamalKeyPair {
        public final BigInteger p, g, h; // Public key
        public final BigInteger x;       // Private key

        public ElGamalKeyPair(BigInteger p, BigInteger g, BigInteger h, BigInteger x) {
            this.p = p;
            this.g = g;
            this.h = h;
            this.x = x;
        }
    }

    // Lớp nội bộ để biểu diễn bản mã
    public static class Ciphertext {
        public final BigInteger c1, c2;

        public Ciphertext(BigInteger c1, BigInteger c2) {
            this.c1 = c1;
            this.c2 = c2;
        }

        // Phép nhân đồng hình (tương đương với phép cộng trong không gian log)
        public Ciphertext multiply(Ciphertext other, BigInteger p) {
            return new Ciphertext(this.c1.multiply(other.c1).mod(p), this.c2.multiply(other.c2).mod(p));
        }
    }

    // Tạo cặp khóa ElGamal
    public ElGamalKeyPair generateKeyPair(int bitLength) {
        BigInteger p = BigInteger.probablePrime(bitLength, random);
        BigInteger g = new BigInteger("2"); // g thường là một giá trị nhỏ
        BigInteger x = new BigInteger(bitLength - 1, random); // Private key
        BigInteger h = g.modPow(x, p); // Public key component
        return new ElGamalKeyPair(p, g, h, x);
    }

    // Mã hóa một tin nhắn (dưới dạng BigInteger)
    public Ciphertext encrypt(BigInteger message, BigInteger p, BigInteger g, BigInteger h) {
        BigInteger y = new BigInteger(p.bitLength() - 1, random);
        BigInteger c1 = g.modPow(y, p);
        BigInteger s = h.modPow(y, p);
        BigInteger c2 = message.multiply(s).mod(p);
        return new Ciphertext(c1, c2);
    }

    // Giải mã một bản mã
    public BigInteger decrypt(Ciphertext ciphertext, BigInteger p, BigInteger x) {
        BigInteger s = ciphertext.c1.modPow(x, p);
        BigInteger sInv = s.modInverse(p);
        return ciphertext.c2.multiply(sInv).mod(p);
    }

    // Cộng (kiểm phiếu) một danh sách các bản mã đã được mã hóa
    public Ciphertext tallyVotes(List<Ciphertext> ciphertexts, BigInteger p) {
        if (ciphertexts == null || ciphertexts.isEmpty()) {
            return null;
        }
        Ciphertext result = ciphertexts.get(0);
        for (int i = 1; i < ciphertexts.size(); i++) {
            result = result.multiply(ciphertexts.get(i), p);
        }
        return result;
    }
}

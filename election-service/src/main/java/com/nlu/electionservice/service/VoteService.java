package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.VoteE2ERequest;
import com.nlu.electionservice.entity.BlindSignatureLog;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.VoteRepository;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class VoteService {

    private final BlindSignatureLogRepository blindSignatureLogRepository;
    private final VoteRepository voteRepository;
    private final CandidateRepository candidateRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.crypto-service-url:http://localhost:8084}")
    private String cryptoServiceUrl;

    @Value("${app.internal-service-token}")
    private String internalToken;

    @Autowired
    public VoteService(BlindSignatureLogRepository blindSignatureLogRepository,
                       VoteRepository voteRepository,
                       CandidateRepository candidateRepository) {
        this.blindSignatureLogRepository = blindSignatureLogRepository;
        this.voteRepository = voteRepository;
        this.candidateRepository = candidateRepository;
    }

    /**
     * Ghi BlindSignatureLog và lưu phiếu bầu trong CÙNG một transaction.
     */
    @Transactional
    public String markAndCastVote(Long userId, VoteE2ERequest request, BigInteger signedToken) throws Exception {
        BlindSignatureLog logEntry = new BlindSignatureLog();
        logEntry.setUserId(userId);
        logEntry.setElectionId(request.getElectionId());
        logEntry.setRoundId(request.getRoundId());
        blindSignatureLogRepository.save(logEntry);

        String receiptCode = generateAnonymousReceipt(request);

        Vote vote = new Vote();
        vote.setElectionId(request.getElectionId());
        vote.setRoundId(request.getRoundId());
        vote.setMessageToken(receiptCode);
        vote.setSignature(signedToken.toString(16));

        if (request.getEncryptedVote() != null && !request.getEncryptedVote().isBlank()) {
            vote.setEncryptedVote(request.getEncryptedVote());
            vote.setCandidateId(null);
        } else {
            vote.setCandidateId(request.getCandidateId());
        }

        voteRepository.save(vote);
        return receiptCode;
    }

    /**
     * Giải mã tất cả phiếu E2E của một vòng, gán candidateId và tăng voteCount.
     * Gọi ngay trước khi tổng hợp kết quả vòng.
     */
    @Transactional
    public void decryptRoundVotes(Long electionId, Long roundId) {
        List<Vote> encryptedVotes = voteRepository
            .findByElectionIdAndRoundIdAndEncryptedVoteIsNotNull(electionId, roundId);

        if (encryptedVotes.isEmpty()) return;

        String cryptoUrl = cryptoServiceUrl + "/api/crypto/decrypt-vote";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.set("Content-Type", "application/json");
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

        for (Vote vote : encryptedVotes) {
            try {
                HttpEntity<Map<String, String>> req = new HttpEntity<>(
                    Map.of("encryptedVote", vote.getEncryptedVote(), "electionId", String.valueOf(electionId)), headers);
                org.springframework.http.ResponseEntity<Map> res =
                    restTemplate.postForEntity(cryptoUrl, req, Map.class);

                if (res.getBody() != null && res.getBody().containsKey("plaintext")) {
                    String plaintext = (String) res.getBody().get("plaintext");
                    Map<String, Object> parsed = om.readValue(plaintext, Map.class);
                    Long candidateId = Long.valueOf(parsed.get("candidateId").toString());
                    vote.setCandidateId(candidateId);
                    vote.setEncryptedVote(null);
                    voteRepository.save(vote);
                    candidateRepository.incrementVoteCount(candidateId);
                }
            } catch (Exception ex) {
                log.error("[decryptRoundVotes] Loi giai ma vote id={}: {}", vote.getId(), ex.getMessage());
            }
        }
    }

    private String generateAnonymousReceipt(VoteE2ERequest request) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String data = request.getElectionId() + ":" + request.getRoundId() + ":"
            + System.nanoTime() + ":" + java.util.UUID.randomUUID();
        byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new BigInteger(1, hash).toString(16);
    }
}

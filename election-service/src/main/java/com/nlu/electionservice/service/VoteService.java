package com.nlu.electionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlu.electionservice.dto.VoteE2ERequest;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.EncryptedVote;
import com.nlu.electionservice.entity.PublicBulletinBoard;
import com.nlu.electionservice.entity.BlindSignatureLog;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.EncryptedVoteRepository;
import com.nlu.electionservice.repository.PublicBulletinBoardRepository;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VoteService {

    private final ElectionRepository electionRepository;
    private final EncryptedVoteRepository encryptedVoteRepository;
    private final PublicBulletinBoardRepository publicBulletinBoardRepository;
    private final HomomorphicEncryptionService encryptionService;
    private final BlindSignatureLogRepository blindSignatureLogRepository;
    private final VoteRepository voteRepository;

    @Autowired
    public VoteService(ElectionRepository electionRepository, 
                       EncryptedVoteRepository encryptedVoteRepository, 
                       PublicBulletinBoardRepository publicBulletinBoardRepository, 
                       HomomorphicEncryptionService encryptionService,
                       BlindSignatureLogRepository blindSignatureLogRepository,
                       VoteRepository voteRepository) {
        this.electionRepository = electionRepository;
        this.encryptedVoteRepository = encryptedVoteRepository;
        this.publicBulletinBoardRepository = publicBulletinBoardRepository;
        this.encryptionService = encryptionService;
        this.blindSignatureLogRepository = blindSignatureLogRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public String castE2EVote(VoteE2ERequest request, BigInteger signedBlindToken, Long userId) throws Exception {
        // Bước 1: Lấy thông tin cuộc bầu cử và khóa công khai ElGamal
        Election election = electionRepository.findById(request.getElectionId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử."));

        BigInteger p = new BigInteger(election.getElGamalP(), 16);
        BigInteger g = new BigInteger(election.getElGamalG(), 16);
        BigInteger h = new BigInteger(election.getElGamalH(), 16);

        // Bước 2: Mã hóa lựa chọn của cử tri
        BigInteger message = BigInteger.valueOf(request.getCandidateId());
        HomomorphicEncryptionService.Ciphertext encryptedChoice = encryptionService.encrypt(message, p, g, h);

        // Bước 3: Tạo mã biên nhận duy nhất
        String receiptCode = generateReceiptCode(request.getElectionId(), encryptedChoice);

        // Bước 4: Lưu lá phiếu đã mã hóa
        EncryptedVote encryptedVote = new EncryptedVote();
        encryptedVote.setElectionId(request.getElectionId());
        encryptedVote.setReceiptCode(receiptCode);
        encryptedVote.setEncryptedChoice("{\"c1\":\"" + encryptedChoice.c1.toString(16) + "\",\"c2\":\"" + encryptedChoice.c2.toString(16) + "\"}");
        encryptedVote.setCastTime(LocalDateTime.now());
        encryptedVoteRepository.save(encryptedVote);

        // Bước 5: Ghi lên Bảng Tin Công Khai
        PublicBulletinBoard pbb = new PublicBulletinBoard();
        pbb.setElectionId(request.getElectionId());
        pbb.setEventType("VOTE_CAST");
        pbb.setPayload(encryptedVote.getEncryptedChoice()); // Chỉ lưu bản mã, không lưu mã biên nhận
        publicBulletinBoardRepository.save(pbb);

        // ĐỂ TƯƠNG THÍCH VỚI LOGIC CŨ (Ví dụ: tính toán tiến trình bầu cử)
        // Chúng ta vẫn tạo một bản ghi ảo trong bảng votes và blind_signature_logs
        BlindSignatureLog log = new BlindSignatureLog();
        log.setUserId(userId);
        log.setElectionId(request.getElectionId());
        log.setRoundId(request.getRoundId());
        blindSignatureLogRepository.save(log);

        Vote vote = new Vote();
        vote.setElectionId(request.getElectionId());
        vote.setRoundId(request.getRoundId());
        vote.setCandidateId(request.getCandidateId());
        vote.setMessageToken(receiptCode); // Dùng receiptCode làm token tạm
        vote.setSignature(signedBlindToken.toString(16));
        voteRepository.save(vote);

        // Bước 6: Trả về mã biên nhận cho cử tri
        return receiptCode;
    }

    private String generateReceiptCode(Long electionId, HomomorphicEncryptionService.Ciphertext ciphertext) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String data = electionId + ciphertext.c1.toString(16) + ciphertext.c2.toString(16) + System.nanoTime();
        byte[] hash = digest.digest(data.getBytes());
        return new BigInteger(1, hash).toString(16);
    }
}

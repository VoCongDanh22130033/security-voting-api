package com.nlu.electionservice.controller;

import com.nlu.electionservice.entity.EncryptedVote;
import com.nlu.electionservice.entity.PublicBulletinBoard;
import com.nlu.electionservice.repository.EncryptedVoteRepository;
import com.nlu.electionservice.repository.PublicBulletinBoardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {

    @Autowired
    private EncryptedVoteRepository encryptedVoteRepository;

    @Autowired
    private PublicBulletinBoardRepository publicBulletinBoardRepository;

    @GetMapping("/receipt/{receiptCode}")
    public ResponseEntity<?> verifyReceipt(@PathVariable String receiptCode) {
        return encryptedVoteRepository.findByReceiptCode(receiptCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // API để tải xuống Bảng Tin Công Khai của một cuộc bầu cử
    @GetMapping("/bulletin-board/{electionId}")
    public ResponseEntity<List<PublicBulletinBoard>> getBulletinBoard(@PathVariable Long electionId) {
        List<PublicBulletinBoard> board = publicBulletinBoardRepository.findByElectionIdOrderByTimestampAsc(electionId);
        return ResponseEntity.ok(board);
    }
}

package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.EncryptedVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EncryptedVoteRepository extends JpaRepository<EncryptedVote, Long> {
    Optional<EncryptedVote> findByReceiptCode(String receiptCode);
}

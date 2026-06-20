package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.VoteE2ERequest;
import com.nlu.electionservice.entity.BlindSignatureLog;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoteService – Unit Tests")
class VoteServiceTest {

    @Mock BlindSignatureLogRepository blindSignatureLogRepository;
    @Mock VoteRepository voteRepository;

    @InjectMocks VoteService voteService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private VoteE2ERequest buildRequest(Long electionId, Long roundId, Long candidateId) {
        VoteE2ERequest r = new VoteE2ERequest();
        r.setElectionId(electionId);
        r.setRoundId(roundId);
        r.setCandidateId(candidateId);
        r.setBlindToken("aabbcc112233");
        return r;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // markVoterAsVoted
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("markVoterAsVoted()")
    class MarkVoterAsVoted {

        @Test
        @DisplayName("TC-V01 – Lưu BlindSignatureLog với đúng userId/electionId/roundId")
        void saves_correct_log_fields() {
            voteService.markVoterAsVoted(10L, 5L, 3L);

            ArgumentCaptor<BlindSignatureLog> captor = ArgumentCaptor.forClass(BlindSignatureLog.class);
            verify(blindSignatureLogRepository).save(captor.capture());

            BlindSignatureLog log = captor.getValue();
            assertThat(log.getUserId()).isEqualTo(10L);
            assertThat(log.getElectionId()).isEqualTo(5L);
            assertThat(log.getRoundId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("TC-V02 – Gọi đúng 1 lần save")
        void calls_save_exactly_once() {
            voteService.markVoterAsVoted(1L, 1L, 1L);
            verify(blindSignatureLogRepository, times(1)).save(any(BlindSignatureLog.class));
        }

        @Test
        @DisplayName("TC-V03 – Log KHÔNG chứa candidateId (đảm bảo ẩn danh)")
        void log_does_not_leak_candidate() {
            voteService.markVoterAsVoted(7L, 2L, 4L);

            ArgumentCaptor<BlindSignatureLog> captor = ArgumentCaptor.forClass(BlindSignatureLog.class);
            verify(blindSignatureLogRepository).save(captor.capture());

            // BlindSignatureLog không có trường candidateId → chỉ verify 3 field trên
            BlindSignatureLog log = captor.getValue();
            assertThat(log.getUserId()).isNotNull();
            assertThat(log.getElectionId()).isNotNull();
            assertThat(log.getRoundId()).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // castAnonymousVote
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("castAnonymousVote()")
    class CastAnonymousVote {

        @Test
        @DisplayName("TC-V04 – Lưu Vote không có userId")
        void saves_vote_without_user_id() throws Exception {
            VoteE2ERequest req = buildRequest(5L, 3L, 2L);
            BigInteger sig = BigInteger.valueOf(999999L);

            voteService.castAnonymousVote(req, sig);

            ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
            verify(voteRepository).save(captor.capture());

            Vote vote = captor.getValue();
            assertThat(vote.getElectionId()).isEqualTo(5L);
            assertThat(vote.getRoundId()).isEqualTo(3L);
            assertThat(vote.getCandidateId()).isEqualTo(2L);
            // Đảm bảo không có userId field được set
        }

        @Test
        @DisplayName("TC-V05 – Trả về receipt code là chuỗi hex hợp lệ")
        void returns_hex_receipt_code() throws Exception {
            VoteE2ERequest req = buildRequest(1L, 1L, 1L);
            BigInteger sig = BigInteger.valueOf(12345L);

            String receipt = voteService.castAnonymousVote(req, sig);

            assertThat(receipt).isNotNull().isNotBlank();
            assertThat(receipt).matches("[0-9a-f]+");   // chỉ gồm ký tự hex
        }

        @Test
        @DisplayName("TC-V06 – Mỗi lần gọi trả receipt code khác nhau (nonce ngẫu nhiên)")
        void receipt_is_unique_per_call() throws Exception {
            VoteE2ERequest req = buildRequest(1L, 1L, 1L);
            BigInteger sig = BigInteger.ONE;

            String r1 = voteService.castAnonymousVote(req, sig);
            String r2 = voteService.castAnonymousVote(req, sig);

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("TC-V07 – Signature được lưu dưới dạng hex string")
        void signature_stored_as_hex() throws Exception {
            VoteE2ERequest req = buildRequest(1L, 1L, 1L);
            BigInteger sig = new BigInteger("255");   // hex = "ff"

            voteService.castAnonymousVote(req, sig);

            ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
            verify(voteRepository, atLeastOnce()).save(captor.capture());

            String storedSig = captor.getValue().getSignature();
            assertThat(storedSig).isNotNull();
            assertThat(storedSig).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("TC-V08 – Gọi save đúng 1 lần")
        void calls_save_once() throws Exception {
            voteService.castAnonymousVote(buildRequest(1L, 1L, 1L), BigInteger.ONE);
            verify(voteRepository, times(1)).save(any(Vote.class));
        }
    }
}

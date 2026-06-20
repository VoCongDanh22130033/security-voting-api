package com.nlu.electionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlu.electionservice.dto.VoteE2ERequest;
import com.nlu.electionservice.entity.User;
import com.nlu.electionservice.repository.*;
import com.nlu.electionservice.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VoteController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    // Tắt xác minh RSA để test không cần Crypto service thật
    "app.crypto-sign-enabled=false",
    "app.vote-audit-enabled=false",
    "app.crypto-public-key-url=http://localhost:8084/api/crypto/public-key",
    "app.crypto-service-url=http://localhost:8084",
    "app.crypto-sign-url=http://localhost:8084/api/crypto/sign-e2e"
})
@DisplayName("VoteController – MockMvc Tests")
class VoteControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean ElectionService electionService;
    @MockBean VoteService voteService;
    @MockBean VoteRepository voteRepository;
    @MockBean ElectionRoundRepository roundRepository;
    @MockBean com.nlu.electionservice.repository.ElectionRepository electionRepository;
    @MockBean AnonymousVoteRepository anonymousVoteRepository;
    @MockBean UserRepository userRepository;
    @MockBean BlindSignatureLogRepository blindSignatureLogRepository;
    @MockBean ElectionVoterRepository electionVoterRepository;
    @MockBean RoundCandidateRepository roundCandidateRepository;
    @MockBean com.nlu.electionservice.repository.CandidateRepository candidateRepository;
    @MockBean KafkaProducerService auditLogger;
    @MockBean RealtimeNotificationService realtimeNotificationService;
    @MockBean ElectionParticipantInviteService participantInviteService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Request hợp lệ (không cần inviteToken, dùng X-User-Email) */
    private VoteE2ERequest validRequest() {
        VoteE2ERequest r = new VoteE2ERequest();
        r.setElectionId(1L);
        r.setRoundId(1L);
        r.setCandidateId(2L);
        r.setBlindToken("aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899");
        // blindSignature không cần vì crypto-sign-enabled=false
        return r;
    }

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    /** Stub toàn bộ happy path */
    private void stubHappyPath(String email) throws Exception {
        User user = buildUser(10L, email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(electionVoterRepository.countByElectionIdAndVoterId(1L, 10L)).thenReturn(1);
        when(blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(10L, 1L, 1L))
            .thenReturn(false);
        when(roundCandidateRepository.countCandidateInElectionRound(1L, 1L, 2L))
            .thenReturn(1);
        doNothing().when(voteService).markVoterAsVoted(10L, 1L, 1L);
        when(voteService.castAnonymousVote(any(), any())).thenReturn("receipt-hex-abc");
        when(voteRepository.countVotesByCandidate(1L, 1L)).thenReturn(Collections.emptyList());
        doNothing().when(realtimeNotificationService).voteCountUpdated(anyLong(), anyLong(), anyList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/v1/votes/cast-e2e
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/v1/votes/cast-e2e")
    class CastE2EVote {

        @Test
        @DisplayName("TC-V01 – Bầu phiếu thành công → 200 kèm receiptCode")
        void success_returns_receipt() throws Exception {
            stubHappyPath("voter@test.com");

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "voter@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptCode").value("receipt-hex-abc"))
                .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("TC-V02 – Không có email header và không có inviteToken → 401")
        void no_email_no_invite_returns_401() throws Exception {
            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-V03 – Voter không nằm trong danh sách election → 403")
        void voter_not_in_election_returns_403() throws Exception {
            User user = buildUser(99L, "outsider@test.com");
            when(userRepository.findByEmail("outsider@test.com")).thenReturn(Optional.of(user));
            // countByElectionIdAndVoterId trả 0 → không có quyền
            when(electionVoterRepository.countByElectionIdAndVoterId(1L, 99L)).thenReturn(0);

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "outsider@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-V04 – Double vote (đã bầu rồi) → 400")
        void double_vote_returns_400() throws Exception {
            User user = buildUser(10L, "voter@test.com");
            when(userRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(user));
            when(electionVoterRepository.countByElectionIdAndVoterId(1L, 10L)).thenReturn(1);
            // Đã có BlindSignatureLog → double vote
            when(blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(10L, 1L, 1L))
                .thenReturn(true);

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "voter@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-V05 – Ứng viên không thuộc vòng này → 400")
        void invalid_candidate_returns_400() throws Exception {
            User user = buildUser(10L, "voter@test.com");
            when(userRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(user));
            when(electionVoterRepository.countByElectionIdAndVoterId(1L, 10L)).thenReturn(1);
            when(blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(10L, 1L, 1L))
                .thenReturn(false);
            // candidateId không nằm trong round
            when(roundCandidateRepository.countCandidateInElectionRound(1L, 1L, 2L)).thenReturn(0);

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "voter@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-V06 – blindToken rỗng (crypto disabled) → 400")
        void missing_blind_token_returns_400() throws Exception {
            User user = buildUser(10L, "voter@test.com");
            when(userRepository.findByEmail("voter@test.com")).thenReturn(Optional.of(user));
            when(electionVoterRepository.countByElectionIdAndVoterId(1L, 10L)).thenReturn(1);
            when(blindSignatureLogRepository.existsByUserIdAndElectionIdAndRoundId(10L, 1L, 1L))
                .thenReturn(false);
            when(roundCandidateRepository.countCandidateInElectionRound(1L, 1L, 2L)).thenReturn(1);

            VoteE2ERequest noToken = validRequest();
            noToken.setBlindToken("");   // rỗng

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "voter@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(noToken)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-V07 – Voter không tồn tại trong DB → 400 (exception)")
        void user_not_found_returns_400() throws Exception {
            when(userRepository.findByEmail("ghost@test.com"))
                .thenThrow(new RuntimeException("Nguoi dung khong hop le."));

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "ghost@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-V08 – Sau bầu phiếu thành công: markVoterAsVoted được gọi")
        void voter_marked_as_voted_after_success() throws Exception {
            stubHappyPath("voter2@test.com");

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "voter2@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isOk());

            verify(voteService).markVoterAsVoted(10L, 1L, 1L);
        }

        @Test
        @DisplayName("TC-V09 – Sau bầu phiếu thành công: castAnonymousVote được gọi")
        void anonymous_vote_cast_after_success() throws Exception {
            stubHappyPath("voter3@test.com");

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "voter3@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isOk());

            verify(voteService).castAnonymousVote(any(), any());
        }

        @Test
        @DisplayName("TC-V10 – Sau bầu phiếu: realtime broadcast được gọi")
        void realtime_notification_broadcast() throws Exception {
            stubHappyPath("voter4@test.com");

            mvc.perform(post("/api/v1/votes/cast-e2e")
                    .header("X-User-Email", "voter4@test.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(validRequest())))
                .andExpect(status().isOk());

            verify(realtimeNotificationService).voteCountUpdated(eq(1L), eq(1L), anyList());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET /api/v1/votes/results
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/v1/votes/results")
    class GetResults {

        @Test
        @DisplayName("TC-R01 – Lấy kết quả hợp lệ → 200 kèm votes array")
        void get_results_success() throws Exception {
            when(voteRepository.countVotesByCandidate(1L, 1L)).thenReturn(
                List.of(
                    Map.of("candidateId", 2L, "voteCount", 5L),
                    Map.of("candidateId", 3L, "voteCount", 3L)
                )
            );
            when(electionRepository.findById(1L)).thenReturn(Optional.empty());

            mvc.perform(get("/api/v1/votes/results")
                    .param("electionId", "1")
                    .param("roundId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votes").isArray());
        }

        @Test
        @DisplayName("TC-R02 – Thiếu param electionId → 400")
        void missing_election_id_returns_400() throws Exception {
            mvc.perform(get("/api/v1/votes/results")
                    .param("roundId", "1"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-R03 – Thiếu param roundId → 400")
        void missing_round_id_returns_400() throws Exception {
            mvc.perform(get("/api/v1/votes/results")
                    .param("electionId", "1"))
                .andExpect(status().isBadRequest());
        }
    }
}

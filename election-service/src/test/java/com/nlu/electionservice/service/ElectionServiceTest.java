package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElectionService – Unit Tests (Round Management)")
class ElectionServiceTest {

    @Mock ElectionRepository electionRepository;
    @Mock com.cloudinary.Cloudinary cloudinary;
    @Mock CandidateRepository candidateRepository;
    @Mock RoundCandidateRepository roundCandidateRepository;
    @Mock ElectionRoundRepository roundRepository;
    @Mock UsedTokenRepository usedTokenRepository;
    @Mock VoteRepository voteRepository;
    @Mock ElectionParticipantInviteService participantInviteService;
    @Mock RealtimeNotificationService realtimeNotificationService;

    @InjectMocks ElectionService electionService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Election buildElection(Long id, String status) {
        Election e = new Election();
        e.setId(id);
        e.setStatus(status);
        e.setTitle("Test Election");
        e.setTotalRounds(2);
        return e;
    }

    private ElectionRound buildRound(Long id, String status, int number) {
        ElectionRound r = new ElectionRound();
        r.setId(id);
        r.setStatus(status);
        r.setRoundNumber(number);
        r.setTitle("Round " + number);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // tryCloseElection
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("tryCloseElection()")
    class TryCloseElection {

        @Test
        @DisplayName("TC-E01 – Election OPEN → đóng thành công, trả true")
        void closes_open_election() {
            Election e = buildElection(1L, "OPEN");
            when(electionRepository.findById(1L)).thenReturn(Optional.of(e));

            boolean result = electionService.tryCloseElection(1L);

            assertThat(result).isTrue();
            ArgumentCaptor<Election> captor = ArgumentCaptor.forClass(Election.class);
            verify(electionRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("TC-E02 – Election đã CLOSED → trả false, không save lại")
        void already_closed_returns_false() {
            Election e = buildElection(1L, "CLOSED");
            when(electionRepository.findById(1L)).thenReturn(Optional.of(e));

            boolean result = electionService.tryCloseElection(1L);

            assertThat(result).isFalse();
            verify(electionRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-E03 – Election không tồn tại → trả false")
        void election_not_found_returns_false() {
            when(electionRepository.findById(99L)).thenReturn(Optional.empty());

            boolean result = electionService.tryCloseElection(99L);

            assertThat(result).isFalse();
            verify(electionRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-E04 – Election UPCOMING → đóng thành công")
        void closes_upcoming_election() {
            Election e = buildElection(2L, "UPCOMING");
            when(electionRepository.findById(2L)).thenReturn(Optional.of(e));

            boolean result = electionService.tryCloseElection(2L);

            assertThat(result).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // tryCloseRound
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("tryCloseRound()")
    class TryCloseRound {

        @Test
        @DisplayName("TC-R01 – Round OPEN → đóng thành công, trả true")
        void closes_open_round() {
            ElectionRound r = buildRound(10L, "OPEN", 1);
            when(roundRepository.findById(10L)).thenReturn(Optional.of(r));

            boolean result = electionService.tryCloseRound(10L);

            assertThat(result).isTrue();
            ArgumentCaptor<ElectionRound> captor = ArgumentCaptor.forClass(ElectionRound.class);
            verify(roundRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("TC-R02 – Round đã CLOSED → trả false, không save lại")
        void already_closed_round_returns_false() {
            ElectionRound r = buildRound(10L, "CLOSED", 1);
            when(roundRepository.findById(10L)).thenReturn(Optional.of(r));

            boolean result = electionService.tryCloseRound(10L);

            assertThat(result).isFalse();
            verify(roundRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-R03 – Round không tồn tại → trả false")
        void round_not_found_returns_false() {
            when(roundRepository.findById(999L)).thenReturn(Optional.empty());

            boolean result = electionService.tryCloseRound(999L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("TC-R04 – Round UPCOMING → đóng thành công")
        void closes_upcoming_round() {
            ElectionRound r = buildRound(20L, "UPCOMING", 2);
            when(roundRepository.findById(20L)).thenReturn(Optional.of(r));

            boolean result = electionService.tryCloseRound(20L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("TC-R05 – Sau khi đóng, status trong DB là CLOSED")
        void saved_round_has_closed_status() {
            ElectionRound r = buildRound(5L, "OPEN", 1);
            when(roundRepository.findById(5L)).thenReturn(Optional.of(r));

            electionService.tryCloseRound(5L);

            verify(roundRepository).save(argThat(round -> "CLOSED".equals(round.getStatus())));
        }
    }
}

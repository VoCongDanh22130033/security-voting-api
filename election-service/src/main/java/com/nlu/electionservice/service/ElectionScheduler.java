package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ElectionScheduler {

  @Autowired
  private ElectionRepository electionRepository;

  @Autowired
  private ElectionRoundRepository roundRepository;
  
  @Autowired
  private com.nlu.electionservice.service.ElectionService electionService;

  @Autowired
  private ElectionParticipantInviteService participantInviteService;

  @Autowired
  private RealtimeNotificationService realtimeNotificationService;

  private final Set<Long> countdownNotifiedRoundIds = ConcurrentHashMap.newKeySet();
  private final Set<Long> processingRoundIds = ConcurrentHashMap.newKeySet();
  private final Set<Long> processingElectionIds = ConcurrentHashMap.newKeySet();

  @Scheduled(fixedRate = 1000)
  public void autoOpenElectionsAndRounds() {
    LocalDateTime now = LocalDateTime.now();
    log.info("NOW JAVA = {}", LocalDateTime.now());
    try {
      List<Election> upcomingElections = electionRepository.findAllByStatusAndStartTimeBefore("UPCOMING", now);
      if (!upcomingElections.isEmpty()) {
        upcomingElections.forEach(election -> {
          if (election.getEndTime() != null && now.isAfter(election.getEndTime())) {
              election.setStatus("CLOSED");
          } else {
              election.setStatus("OPEN");
              log.info(">>> [SCHEDULER] Election ID: {} has been automatically opened.", election.getId());
              realtimeNotificationService.electionOpened(election);
          }
        });
        electionRepository.saveAll(upcomingElections);
      }
    } catch (Exception e) {
      log.error("Error while opening elections: {}", e.getMessage());
    }

    try {
      List<ElectionRound> upcomingRounds = roundRepository.findUpcomingRoundsByStatusAndStartTimeBefore("UPCOMING", now);
      if (!upcomingRounds.isEmpty()) {
        for (ElectionRound round : upcomingRounds) {
          // Bỏ giới hạn roundNumber == 1 để các vòng sau nếu có thời gian chờ (gap)
          // vẫn có thể tự động mở khi tới thời gian startTime

          // Kiểm tra nếu vòng trước đó chưa đóng thì không được mở vòng này
          if (round.getRoundNumber() > 1) {
             java.util.Optional<ElectionRound> prevRound = roundRepository.findByElectionIdAndRoundNumber(round.getElection().getId(), round.getRoundNumber() - 1);
             if (prevRound.isPresent() && !"CLOSED".equals(prevRound.get().getStatus()) && !"CANCELLED".equals(prevRound.get().getStatus())) {
                 log.warn("Cannot open round {} because round {} is still {}", round.getRoundNumber(), prevRound.get().getRoundNumber(), prevRound.get().getStatus());
                 continue; // Bỏ qua, chờ vòng trước đóng hẳn
             }
          }

          if (round.getEndTime() != null && now.isAfter(round.getEndTime())) {
              round.setStatus("CLOSED");
          } else {
              round.setStatus("OPEN");
              log.info(">>> [SCHEDULER] Round number {} of Election ID: {} has been automatically opened!",
                  round.getRoundNumber(), round.getElection().getId());
              try {
                participantInviteService.sendRoundInvitations(round.getElection().getId(), round.getRoundNumber());
                realtimeNotificationService.roundOpened(round);
              } catch (Exception inviteEx) {
                log.error("Error sending round invitations: {}", inviteEx.getMessage());
              }
          }
          roundRepository.save(round);
        }
      }
    } catch (Exception e) {
      log.error("Error while opening rounds: {}", e.getMessage());
    }
  }

  @Scheduled(fixedRate = 1000)
  public void autoCloseElectionsAndRounds() {
    LocalDateTime now = LocalDateTime.now();

    try {
      List<ElectionRound> activeRounds =
          roundRepository.findActiveRoundsByStatusAndEndTimeLessThanEqual("OPEN", now);
      for (ElectionRound round : activeRounds) {
        if (!processingRoundIds.add(round.getId())) {
          continue;
        }

        try {
          log.info(">>> [SCHEDULER] Closing round {} of election {}",
              round.getRoundNumber(), round.getElection().getId());

          electionService.processRoundAfterClose(
              round.getElection().getId(),
              round.getId()
          );

        } catch (Exception ex) {
          log.error("Error processing round results: {}", ex.getMessage(), ex);
        } finally {
          processingRoundIds.remove(round.getId());
        }
      }
    } catch (Exception e) {
      log.error("Error while closing rounds: {}", e.getMessage(), e);
    }

    try {
      List<Election> expiredElections =
          electionRepository.findAllByStatusAndEndTimeBefore("OPEN", now);

      for (Election election : expiredElections) {
        if (!processingElectionIds.add(election.getId())) {
          continue;
        }
        try {
          boolean closed = electionService.tryCloseElection(election.getId());
          if (closed) {
            log.info(">>> [SCHEDULER] Election {} closed via direct path.", election.getId());
            try {
              realtimeNotificationService.electionClosed(election);
            } catch (Exception ex) {
              log.error("Error sending election closed notification: {}", ex.getMessage(), ex);
            }
            try {
              electionService.determineElectionWinner(election.getId());
            } catch (Exception ex) {
              log.error("Error determining election winner: {}", ex.getMessage(), ex);
            }
          }
        } finally {
          processingElectionIds.remove(election.getId());
        }
      }
    } catch (Exception e) {
      log.error("Error while closing elections: {}", e.getMessage(), e);
    }

    // Safety net: nếu tất cả rounds đã CLOSED nhưng election vẫn OPEN → force close
    try {
      List<Election> openElections = electionRepository.findAllByStatus("OPEN");
      for (Election election : openElections) {
        List<ElectionRound> rounds = roundRepository.findByElectionId(election.getId());
        if (rounds.isEmpty()) continue;
        boolean allRoundsDone = rounds.stream()
            .allMatch(r -> "CLOSED".equals(r.getStatus()) || "CANCELLED".equals(r.getStatus()));
        if (allRoundsDone) {
          log.warn(">>> [SCHEDULER SAFETY] Election {} has all rounds done but is still OPEN — forcing close.", election.getId());
          boolean closed = electionService.tryCloseElection(election.getId());
          if (closed) {
            try { realtimeNotificationService.electionClosed(election); } catch (Exception ex) {
              log.error("Error sending election closed notification (safety): {}", ex.getMessage());
            }
            try { electionService.determineElectionWinner(election.getId()); } catch (Exception ex) {
              log.error("Error determining winner (safety): {}", ex.getMessage());
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Error in safety-net election close check: {}", e.getMessage(), e);
    }
  }

  @Scheduled(fixedRate = 60000)
  public void notifyRoundsEndingSoon() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime inFifteenMinutes = now.plusMinutes(15);

    try {
      List<ElectionRound> endingSoonRounds = roundRepository.findByStatusAndEndTimeBetween("OPEN", now, inFifteenMinutes);
      for (ElectionRound round : endingSoonRounds) {
        if (!countdownNotifiedRoundIds.add(round.getId())) {
          continue;
        }
        long minutesLeft = Math.max(1, Duration.between(now, round.getEndTime()).toMinutes());
        realtimeNotificationService.countdown(round, minutesLeft);
      }
    } catch (Exception e) {
      log.error("Error while sending countdown notifications: {}", e.getMessage());
    }
  }
}

package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

  @Scheduled(fixedRate = 5000)
  public void autoOpenElectionsAndRounds() {
    LocalDateTime now = LocalDateTime.now();

    try {
      List<Election> upcomingElections = electionRepository.findAllByStatusAndStartTimeBefore("UPCOMING", now);
      if (!upcomingElections.isEmpty()) {
        upcomingElections.forEach(election -> {
          election.setStatus("OPEN");
          log.info(">>> [SCHEDULER] Election ID: {} has been automatically opened.", election.getId());
        });
        electionRepository.saveAll(upcomingElections);
      }
    } catch (Exception e) {
      log.error("Error while opening elections: {}", e.getMessage());
    }

    try {
      List<ElectionRound> upcomingRounds = roundRepository.findUpcomingRoundsByStatusAndStartTimeBefore("UPCOMING", now);
      if (!upcomingRounds.isEmpty()) {
        upcomingRounds.forEach(round -> {
          round.setStatus("OPEN");
          log.info(">>> [SCHEDULER] Round number {} of Election ID: {} has been automatically opened!",
              round.getRoundNumber(), round.getElection().getId());
          try {
            participantInviteService.sendRoundInvitations(round.getElection().getId(), round.getRoundNumber());
          } catch (Exception inviteEx) {
            log.error("Error sending round invitations: {}", inviteEx.getMessage());
          }
        });
        roundRepository.saveAll(upcomingRounds);
      }
    } catch (Exception e) {
      log.error("Error while opening rounds: {}", e.getMessage());
    }
  }

  @Scheduled(fixedRate = 5000)
  public void autoCloseElectionsAndRounds() {
    LocalDateTime now = LocalDateTime.now();

    try {
      List<ElectionRound> activeRounds = roundRepository.findActiveRoundsByStatusAndEndTimeBefore("OPEN", now);
      if (!activeRounds.isEmpty()) {
        for (ElectionRound round : activeRounds) {
          round.setStatus("CLOSED");
          log.info(">>> [SCHEDULER] Round number {} of Election ID: {} has ended.",
              round.getRoundNumber(), round.getElection().getId());
          roundRepository.save(round);
          try {
            electionService.processRoundAfterClose(round.getElection().getId(), round.getId());
          } catch (Exception ex) {
            log.error("Error processing round results: {}", ex.getMessage());
          }
        }
      }
    } catch (Exception e) {
      log.error("Error while closing rounds: {}", e.getMessage());
    }

    try {
      List<Election> expiredElections = electionRepository.findAllByStatusAndEndTimeBefore("OPEN", now);
      if (!expiredElections.isEmpty()) {
        expiredElections.forEach(election -> {
          election.setStatus("CLOSED");
          log.info(">>> [SCHEDULER] Election ID: {} has been automatically closed.", election.getId());
          electionRepository.save(election);
          try {
            electionService.determineElectionWinner(election.getId());
          } catch (Exception ex) {
            log.error("Error determining election winner: {}", ex.getMessage());
          }
        });
      }
    } catch (Exception e) {
      log.error("Error while closing elections: {}", e.getMessage());
    }
  }
}

package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.entity.RoundCandidate;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import com.nlu.electionservice.repository.RoundCandidateRepository;
import com.nlu.electionservice.repository.VoteRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoundService {

  @Autowired
  private ElectionRoundRepository roundRepository;

  @Autowired
  private ElectionRepository electionRepository;

  @Autowired
  private RoundCandidateRepository roundCandidateRepository;

  @Autowired
  private VoteRepository voteRepository;

  @Autowired
  @Lazy
  private ElectionService electionService;

  @Autowired
  private ElectionParticipantInviteService participantInviteService;

  public List<ElectionRound> getRoundsForElection(Long electionId) {
    return roundRepository.findByElectionId(electionId);
  }

  @Transactional
  public ElectionRound startRound(Long roundId) {
    ElectionRound round = roundRepository.findById(roundId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử"));
    round.setStatus("OPEN");
    round.setStartTime(LocalDateTime.now());
    ElectionRound saved = roundRepository.save(round);

    // Set election OPEN nếu chưa
    Long electionId = round.getElection().getId();
    electionRepository.findById(electionId).ifPresent(election -> {
      if (!"OPEN".equals(election.getStatus())) {
        election.setStatus("OPEN");
        electionRepository.save(election);
      }
    });

    // Gửi email mời tham gia vòng mới
    try {
      participantInviteService.sendRoundInvitations(electionId, round.getRoundNumber());
    } catch (Exception e) {
      log.warn("Không gửi được email vòng {}: {}", round.getRoundNumber(), e.getMessage());
    }
    return saved;
  }

  @Transactional
  public ElectionRound closeRound(Long roundId) {
    ElectionRound round = roundRepository.findById(roundId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử"));
    round.setStatus("CLOSED");
    round.setEndTime(LocalDateTime.now());
    ElectionRound saved = roundRepository.save(round);
    // Giải mã phiếu, gửi email, broadcast kết quả sau khi đóng vòng
    Long electionId = round.getElection().getId();
    try {
      electionService.processRoundAfterClose(electionId, roundId);
    } catch (Exception e) {
      log.warn("processRoundAfterClose lỗi vòng {}: {}", roundId, e.getMessage());
    }
    return saved;
  }

  public List<Map<String, Object>> tallyRound(Long electionId, Long roundId) {
    return voteRepository.countVotesByCandidate(electionId, roundId);
  }

  @Transactional
  public Map<String, Object> advanceRound(Long roundId) {
    ElectionRound round = roundRepository.findById(roundId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy vòng bầu cử"));

    Long electionId = round.getElection().getId();
    Integer maxAdvance = round.getMaxAdvanceCount() != null && round.getMaxAdvanceCount() > 0 ? round.getMaxAdvanceCount() : 1;

    // Tally votes
    List<Map<String, Object>> tallies = tallyRound(electionId, roundId);

    // Map candidateId -> votes
    Map<Long, Long> votesMap = new HashMap<>();
    for (Map<String, Object> row : tallies) {
      Object cid = row.get("candidateId");
      Object vc = row.get("voteCount");
      Long candidateId = cid instanceof Number ? ((Number) cid).longValue() : Long.parseLong(cid.toString());
      Long voteCount = vc instanceof Number ? ((Number) vc).longValue() : Long.parseLong(vc.toString());
      votesMap.put(candidateId, voteCount);
    }

    // Load round candidates (all participants)
    List<RoundCandidate> participants = roundCandidateRepository.findByRoundId(roundId);

    // Sort participants by votes desc
    participants.sort((a, b) -> {
      Long va = votesMap.getOrDefault(a.getCandidateId(), 0L);
      Long vb = votesMap.getOrDefault(b.getCandidateId(), 0L);
      return vb.compareTo(va);
    });

    // Determine advanced set
    Set<Long> advanced = new HashSet<>();
    for (int i = 0; i < Math.min(maxAdvance, participants.size()); i++) {
      advanced.add(participants.get(i).getCandidateId());
    }


    // Prepare next round candidates
    Integer nextRoundNumber = round.getRoundNumber() + 1;
    Optional<ElectionRound> nextRoundOpt = roundRepository.findByElectionIdAndRoundNumber(electionId, nextRoundNumber);
    if (nextRoundOpt.isPresent()) {
      ElectionRound nextRound = nextRoundOpt.get();
      roundCandidateRepository.deleteAll(roundCandidateRepository.findByRoundId(nextRound.getId()));
      // create RoundCandidate entries for advanced candidates
      List<RoundCandidate> nextCandidates = new ArrayList<>();
      for (Long cid : advanced) {
        RoundCandidate rc = new RoundCandidate();
        rc.setRound(nextRound);
        rc.setCandidateId(cid);
        nextCandidates.add(rc);
      }
      roundCandidateRepository.saveAll(nextCandidates);
    }

    Map<String, Object> resp = new HashMap<>();
    resp.put("advancedCandidateIds", advanced);
    resp.put("tallies", tallies);
    return resp;
  }
}


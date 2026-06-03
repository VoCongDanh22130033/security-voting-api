package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CandidateService {

  @Autowired
  private CandidateRepository candidateRepository;

  @Autowired
  private ElectionRoundRepository roundRepository;

  private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

  public List<CandidateResponse> getCandidatesWithVotes(Long electionId) {
    log.info(">>> [BE] Đang lấy danh sách ứng viên cho cuộc bầu cử ID: {}", electionId);

    Optional<ElectionRound> openRoundOpt = roundRepository.findByElectionIdAndStatus(electionId, "OPEN")
        .map(obj -> (ElectionRound) obj);

    if (openRoundOpt.isPresent()) {
      Long roundId = openRoundOpt.get().getId();
      log.info(">>> [BE] Tìm thấy vòng đang MỞ. Lấy ứng viên cho vòng ID: {}", roundId);
      return getCandidatesByRound(roundId);
    }

    List<ElectionRound> allRounds = roundRepository.findByElectionId(electionId);
    Optional<ElectionRound> firstUpcomingRoundOpt = allRounds.stream()
        .filter(r -> "UPCOMING".equalsIgnoreCase(r.getStatus()))
        .min(Comparator.comparing(ElectionRound::getRoundNumber));

    if (firstUpcomingRoundOpt.isPresent()) {
      Long roundId = firstUpcomingRoundOpt.get().getId();
      log.info(">>> [BE] Không có vòng MỞ. Tìm thấy vòng SẮP DIỄN RA. Lấy ứng viên cho vòng ID: {}", roundId);
      return getCandidatesByRound(roundId);
    }

    if (allRounds.isEmpty()) {
      log.warn(">>> [BE] Không tìm thấy bất kỳ vòng nào cho cuộc bầu cử ID: {}. Trả về danh sách rỗng.", electionId);
      return Collections.emptyList();
    }

    Optional<ElectionRound> latestRoundOpt = allRounds.stream()
        .max(Comparator.comparing(ElectionRound::getRoundNumber));

    if (latestRoundOpt.isPresent()) {
      Long roundId = latestRoundOpt.get().getId();
      log.info(">>> [BE] Không có vòng MỞ hoặc SẮP DIỄN RA. Lấy ứng viên từ vòng cuối cùng (ID: {}).", roundId);
      return getCandidatesByRound(roundId);
    }

    return Collections.emptyList();
  }

  public List<CandidateResponse> getCandidatesByRound(Long roundId) {
    log.info(">>> [BE] Đang lấy danh sách ứng viên cho vòng bầu cử ID cụ thể: {}", roundId);

    List<Object[]> results = candidateRepository.findCandidatesByRoundWithVotes(roundId);
    log.info(">>> [BE] Số lượng ứng viên lấy được từ Database cho vòng {}: {}", roundId, results.size());

    return results.stream().map(row -> {
      CandidateResponse dto = new CandidateResponse();
      dto.setId(((Number) row[0]).longValue());
      dto.setName((String) row[1]);
      dto.setDescription((String) row[2]);
      dto.setImageUrl((String) row[3]);
      // SỬA LỖI: Gán giá trị voteCount vào DTO
      dto.setVoteCount(row[4] != null ? ((Number) row[4]).longValue() : 0L);
      log.info(">>> [BE] Ứng viên: {} - Số phiếu: {}", dto.getName(), dto.getVoteCount());
      return dto;
    }).collect(Collectors.toList());
  }

  public List<Candidate> getCandidatesByRoundId(Long roundId) {
    return candidateRepository.findAllByRoundId(roundId);
  }

  public List<Candidate> getAllCandidates() {
      return candidateRepository.findAll();
  }
}
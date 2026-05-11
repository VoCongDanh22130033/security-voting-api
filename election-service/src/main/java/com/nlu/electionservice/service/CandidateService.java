package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.CandidateRequest;
import com.nlu.electionservice.dto.CandidateResponse;
import com.nlu.electionservice.dto.VoteRequest;
import com.nlu.electionservice.entity.AnonymousVote;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.repository.AnonymousVoteRepository;
import com.nlu.electionservice.repository.CandidateRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class CandidateService {

  @Autowired
  private CandidateRepository candidateRepository;
  @Autowired
  private AnonymousVoteRepository anonymousVoteRepository;
  private static final Logger log = LoggerFactory.getLogger(CandidateService.class);
  public List<CandidateResponse> getCandidatesWithVotes(Long electionId) {
    log.info(">>> [BE] Đang lấy danh sách ứng viên cho cuộc bầu cử ID: {}", electionId);

    List<Object[]> results = candidateRepository.findCandidatesWithVoteCountNative(electionId);
    log.info(">>> [BE] Số lượng dòng lấy được từ Database: {}", results.size());

    return results.stream().map(row -> {
      CandidateResponse dto = new CandidateResponse();
      dto.setId(((Number) row[0]).longValue());
      dto.setName((String) row[1]);
      dto.setDescription((String) row[2]);
      dto.setImageUrl((String) row[3]);
      long votes = row[4] != null ? ((Number) row[4]).longValue() : 0L;
      log.info(">>> [BE] Ứng viên: {} - Số phiếu đếm được: {}", row[1], votes);

      return dto;
    }).collect(Collectors.toList());
  }

}
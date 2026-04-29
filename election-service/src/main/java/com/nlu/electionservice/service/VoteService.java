package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.VoteRequest;
import com.nlu.electionservice.entity.AnonymousVote;
import com.nlu.electionservice.repository.ElectionVoterRepository;
import com.nlu.electionservice.repository.AnonymousVoteRepository;
import com.nlu.electionservice.repository.VoterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {
  @Autowired
  private AnonymousVoteRepository anonymousVoteRepository;
  @Autowired
  private ElectionVoterRepository electionVoterRepository;
  @Autowired
  private VoterRepository voterRepository;
  @Transactional
  public void saveVote(VoteRequest request, String email) {
    Long realVoterId = voterRepository.findVoterIdByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin cử tri"));
    if (electionVoterRepository.countByElectionIdAndVoterId(request.getElectionId(), realVoterId) > 0) {
      throw new RuntimeException("Bạn đã bỏ phiếu cho cuộc bầu cử này rồi!");
    }
    AnonymousVote vote = new AnonymousVote();
    vote.setElectionId(request.getElectionId());
    vote.setBlindedContent(request.getBlindedContent());
    vote.setCandidateId(request.getCandidateId());
    vote.setSignature(request.getSignature());
    anonymousVoteRepository.save(vote);
    electionVoterRepository.insertElectionVoter(request.getElectionId(), realVoterId);
  }
}
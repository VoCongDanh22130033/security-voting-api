package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.VoteRequest;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {
  @Autowired
  private VoteRepository voteRepository;

  @Transactional
  public void saveVote(VoteRequest request, String username) {
    Vote vote = new Vote();
    vote.setElectionId(request.getElectionId());

    // Chuyển ID ứng viên thành chuỗi để lưu vào cột TEXT 'encrypted_vote'
    if (request.getCandidateId() != null) {
      vote.setEncryptedVote("CandidateID: " + request.getCandidateId());
    }

    vote.setSignature(request.getSignature());
    vote.setIsValid(true);

    // Lưu ý: Không cần setCreatedAt nếu Vote.java để insertable = false
    // vì Database sẽ tự điền current_timestamp

    voteRepository.save(vote);
    System.out.println(">>> User " + username + " voted for candidate " + request.getCandidateId());
  }
}
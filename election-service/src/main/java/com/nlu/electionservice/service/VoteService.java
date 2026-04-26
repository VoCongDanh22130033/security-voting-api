package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.VoteRequest;
import com.nlu.electionservice.entity.Vote;
import com.nlu.electionservice.repository.ElectionVoterRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.repository.VoterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class VoteService {
  @Autowired
  private VoteRepository voteRepository; // Dùng để lưu bảng 'votes'

  @Autowired
  private ElectionVoterRepository electionVoterRepository; // Dùng để lưu bảng 'election_voters'

  @Autowired
  private VoterRepository voterRepository; // PHẢI DÙNG VoterRepository Ở ĐÂY

  @Transactional
  public void saveVote(VoteRequest request, String username) {
    // 1. Tìm đúng thực thể Voter để lấy ID
    Long realVoterId = voterRepository.findByUsername(username)
        .map(voter -> voter.getId()) // Lúc này getId() sẽ hoạt động vì đã dùng đúng VoterRepository
        .orElseThrow(() -> new RuntimeException("Cử tri chưa được định danh!"));

    // 2. Kiểm tra chặn bầu lần 2
    if (electionVoterRepository.countByElectionIdAndVoterId(request.getElectionId(), realVoterId) > 0) {
      throw new RuntimeException("Bạn đã bỏ phiếu rồi!");
    }

    // 3. Lưu phiếu bầu ẩn danh vào bảng 'votes'
    Vote vote = new Vote();
    vote.setElectionId(request.getElectionId());
    vote.setEncryptedVote(String.valueOf(request.getCandidateId()));
    vote.setSignature(request.getSignature());
    vote.setIsValid(true);
    voteRepository.save(vote); // Dùng voteRepository chuẩn

    // 4. CHỐT: Ghi vào bảng election_voters để không cho vote lại
    electionVoterRepository.insertElectionVoter(request.getElectionId(), realVoterId);
  }
}
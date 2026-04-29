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

  // SỬA LỖI: Đưa khai báo lên đây và thêm @Autowired
  @Autowired
  private VoterRepository voterRepository;

  @Transactional
  public void saveVote(VoteRequest request, String email) {
    // 1. Lấy voterId từ email thông qua Bean đã được tiêm vào
    Long realVoterId = voterRepository.findVoterIdByEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin cử tri"));

    // 2. Chống bầu 2 lần dựa trên bảng election_voters[cite: 18, 19, 21]
    if (electionVoterRepository.countByElectionIdAndVoterId(request.getElectionId(), realVoterId) > 0) {
      throw new RuntimeException("Bạn đã bỏ phiếu cho cuộc bầu cử này rồi!");
    }

    // 3. Lưu vào hòm phiếu ẩn danh (Bảng anonymous_votes)[cite: 18, 20]
    AnonymousVote vote = new AnonymousVote();
    vote.setElectionId(request.getElectionId());

    // Đảm bảo lấy nội dung đã mã hóa/mù từ request
    vote.setBlindedContent(request.getEncryptedVote());

    // Gán signature để cột trong DB không bị NULL[cite: 13, 19]
    vote.setSignature(request.getSignature());

    anonymousVoteRepository.save(vote);

    // 4. Lưu vết vào bảng election_voters để đánh dấu người dùng đã bầu[cite: 18, 21]
    electionVoterRepository.insertElectionVoter(request.getElectionId(), realVoterId);
  }
}
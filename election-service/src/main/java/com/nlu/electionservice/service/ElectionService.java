package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.ElectionRequest;
import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ElectionService {
  @Autowired
  private ElectionRepository electionRepository;

  @Autowired
  private CandidateRepository candidateRepository;

  public List<Election> getAllElections() {
    return electionRepository.findAll();
  }

  @Transactional
  public Election createElection(Election election, List<Candidate> candidates) {
    // Thiết lập các giá trị mặc định
    election.setStatus("OPEN");
    election.setIsDelete(1);

    Election saved = electionRepository.save(election);

    // Lưu danh sách ứng viên nếu có[cite: 10]
    if (candidates != null && !candidates.isEmpty()) {
      candidates.forEach(c -> c.setElection(saved));
      candidateRepository.saveAll(candidates);
    }
    return saved;
  }

  @Transactional
  public Election updateElection(Long id, ElectionRequest request) {
    Election election = electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử"));

    election.setTitle(request.getTitle());
    election.setDescription(request.getDescription());
    election.setStartTime(request.getStartTime());
    election.setEndTime(request.getEndTime());
    election.setRoleId(request.getRoleId());

    // Sử dụng setImage đồng bộ[cite: 10, 11]
    election.setImage(request.getImageUrl());

    // Cập nhật lại ứng viên[cite: 10]
    candidateRepository.deleteByElectionId(id);
    saveCandidates(election, request);

    return electionRepository.save(election);
  }


  @Transactional
  public void deleteElection(Long id) {
    Election election = electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử"));
    election.setIsDelete(2);
    electionRepository.save(election);
  }
  private void saveCandidates(Election election, ElectionRequest request) {
    if (request.getCandidates() != null) {
      List<Candidate> candidates = request.getCandidates().stream().map(cReq -> {
        Candidate c = new Candidate();
        c.setName(cReq.getName());
        c.setDescription(cReq.getDescription());
        c.setElection(election);
        return c;
      }).collect(Collectors.toList());
      candidateRepository.saveAll(candidates);
    }
  }

  public List<Candidate> getCandidatesByElection(Long electionId) {
    boolean exists = electionRepository.existsById(electionId);
    if (!exists) {
      return Collections.emptyList();
    }
    return candidateRepository.findByElectionId(electionId);
  }

  public Election getById(Long id) {
    return electionRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc bầu cử với ID: " + id));
  }
}
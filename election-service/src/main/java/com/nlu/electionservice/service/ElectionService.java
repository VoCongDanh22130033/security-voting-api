package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.CandidateRequest;
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
    election.setStatus("OPEN");
    election.setIsDelete(1);

    Election saved = electionRepository.save(election);

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
    election.setImage(request.getImageUrl());
    election.setStatus(request.getStatus());

    election.getCandidates().clear();

    if (request.getCandidates() != null) {
      request.getCandidates().forEach(cReq -> {
        Candidate candidate = new Candidate();
        candidate.setName(cReq.getName());
        candidate.setDescription(cReq.getDescription());
        candidate.setImageUrl(cReq.getImageUrl());
        candidate.setElection(election);
        election.getCandidates().add(candidate);
      });
    }

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
        c.setImageUrl(cReq.getImageUrl());
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
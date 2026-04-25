package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Candidate;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ElectionService {
  @Autowired
  private ElectionRepository electionRepository;

  @Autowired
  private CandidateRepository candidateRepository;

  public List<Election> getAllElections() {
    return electionRepository.findAll();
  }

  public List<Candidate> getCandidatesByElection(Long electionId) {
    return candidateRepository.findByElectionId(electionId);
  }
}
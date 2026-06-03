package com.nlu.electionservice.service.impl;

import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.UserRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private ElectionRepository electionRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalVoters", userRepository.countByRoles_Name("ROLE_VOTER"));
        statistics.put("totalElections", electionRepository.count());
        statistics.put("openElections", electionRepository.countByStatus("OPEN"));
        statistics.put("closedElections", electionRepository.countByStatus("CLOSED") + electionRepository.countByStatus("ENDED"));
        statistics.put("totalSubmittedBallots", voteRepository.count());
        statistics.put("totalCandidates", candidateRepository.count());
        return statistics;
    }
}

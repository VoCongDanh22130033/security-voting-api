package com.nlu.electionservice.service.impl;

import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import com.nlu.electionservice.repository.CandidateRepository;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionVoterInviteRepository;
import com.nlu.electionservice.repository.UserRepository;
import com.nlu.electionservice.repository.VoteRepository;
import com.nlu.electionservice.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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

    @Autowired
    private BlindSignatureLogRepository blindSignatureLogRepository;

    @Autowired
    private ElectionVoterInviteRepository electionVoterInviteRepository;

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> statistics = new LinkedHashMap<>();

        // === Tổng quan ===
        statistics.put("totalUsers", userRepository.count());
        statistics.put("totalVoters", userRepository.countByRoles_Name("ROLE_VOTER"));
        statistics.put("totalOrganizers", userRepository.countByRoles_Name("ROLE_ORGANIZER"));
        statistics.put("totalAdmins", userRepository.countByRoles_Name("ROLE_ADMIN"));
        statistics.put("totalElections", electionRepository.count());
        statistics.put("openElections", electionRepository.countByStatus("OPEN"));
        long closedElections = electionRepository.countByStatus("CLOSED") + electionRepository.countByStatus("ENDED");
        statistics.put("closedElections", closedElections);
        statistics.put("totalVotes", voteRepository.count());
        statistics.put("totalCandidates", candidateRepository.count());

        // === Hoạt động hôm nay ===
        // todayVotes: Vote entity không có timestamp field → trả 0
        statistics.put("todayVotes", 0);
        // monthlyElections: Election không có createdAt trực tiếp truy vấn → trả 0
        statistics.put("monthlyElections", 0);
        // todayLogins: lấy từ audit-service, không có repo ở đây → trả 0
        statistics.put("todayLogins", 0);

        // === Bảo mật ===
        statistics.put("lockedAccounts", userRepository.countByIsLock(1));
        statistics.put("totalBlindTokens", blindSignatureLogRepository.count());

        // === Tỷ lệ tham gia ===
        long totalInvited = electionVoterInviteRepository.count();
        long totalTokens = blindSignatureLogRepository.count();
        double participationRate = 0.0;
        if (totalInvited > 0) {
            participationRate = Math.round((double) totalTokens / totalInvited * 1000.0) / 10.0;
        }
        statistics.put("participationRate", participationRate);

        // backward-compat aliases
        statistics.put("totalSubmittedBallots", voteRepository.count());

        return statistics;
    }
}

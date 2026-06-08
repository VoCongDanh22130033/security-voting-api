package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.BlindSignatureLog;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.BlindSignatureLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final BlindSignatureLogRepository blindSignatureLogRepository;
    private final ElectionRepository electionRepository;

    @Autowired
    public UserService(BlindSignatureLogRepository blindSignatureLogRepository, ElectionRepository electionRepository) {
        this.blindSignatureLogRepository = blindSignatureLogRepository;
        this.electionRepository = electionRepository;
    }

    public List<Election> getVotingHistory(Long userId) {
        // Lấy danh sách các log chữ ký của user
        List<BlindSignatureLog> logs = blindSignatureLogRepository.findByUserId(userId);

        // Trích xuất ra danh sách các electionId (không trùng lặp)
        List<Long> electionIds = logs.stream()
                .map(BlindSignatureLog::getElectionId)
                .distinct()
                .collect(Collectors.toList());

        // Truy vấn thông tin chi tiết của các cuộc bầu cử đó
        return electionRepository.findAllById(electionIds);
    }
}

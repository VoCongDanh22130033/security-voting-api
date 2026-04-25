package com.nlu.electionservice.repository;



import com.nlu.electionservice.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
  // Tìm danh sách ứng viên dựa trên ID cuộc bầu cử
  List<Candidate> findByElectionId(Long electionId);
}
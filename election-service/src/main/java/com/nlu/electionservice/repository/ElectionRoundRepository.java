package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.ElectionRound;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository // Đánh dấu để Spring Boot tự động nhận diện và quản lý Bean
public interface ElectionRoundRepository extends JpaRepository<ElectionRound, Long> {

  // Tìm danh sách tất cả các vòng thuộc về một cuộc bầu cử cụ thể
  // Spring sẽ tự sinh: SELECT * FROM election_rounds WHERE election_id = :electionId
  List<ElectionRound> findByElectionId(Long electionId);

  // Tìm một vòng cụ thể dựa vào ID cuộc bầu cử và số thứ tự vòng (Ví dụ: Tìm Vòng 2 của cuộc bầu cử ID 5)
  Optional<ElectionRound> findByElectionIdAndRoundNumber(Long electionId, Integer roundNumber);

  List<ElectionRound> findByStatusAndEndTimeBefore(String open, LocalDateTime now);

  List<ElectionRound> findByStatusAndStartTimeBefore(String upcoming, LocalDateTime now);
}
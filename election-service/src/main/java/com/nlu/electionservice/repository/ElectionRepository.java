package com.nlu.electionservice.repository;


import com.nlu.electionservice.entity.Election;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ElectionRepository extends JpaRepository<Election, Long> {
  @Query(value = "SELECT * FROM elections WHERE is_delete = 2", nativeQuery = true)
  List<Election> findAllDeletedElections();
  // kết thúc bầu cử
  List<Election> findAllByStatusAndEndTimeBefore(String status, LocalDateTime now);
  // bắt đầu bầu cử
  List<Election> findAllByStatusAndStartTimeBefore(String upcoming, LocalDateTime now);
}
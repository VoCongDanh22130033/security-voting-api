package com.nlu.electionservice.repository;



import com.nlu.electionservice.entity.Voter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface VoterRepository extends JpaRepository<Voter, Long> {
  // Truy vấn lấy Voter dựa trên username của bảng users
  @Query(value = "SELECT v.* FROM voters v JOIN users u ON v.user_id = u.id WHERE u.username = :uname", nativeQuery = true)
  Optional<Voter> findByUsername(@Param("uname") String username);
}
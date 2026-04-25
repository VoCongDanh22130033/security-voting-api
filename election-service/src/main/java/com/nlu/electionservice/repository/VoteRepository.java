package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.Vote; // Bạn cần tạo Entity Vote tương ứng table votes
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {
}
package com.nlu.electionservice.repository;

import com.nlu.electionservice.entity.AnonymousVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnonymousVoteRepository extends JpaRepository<AnonymousVote, Long> {

}
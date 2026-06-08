package com.nlu.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoteAnonymousRequest {
    private Long electionId;
    private Long roundId;
    private Long candidateId;
    private String messageToken;
    private String signature;
    private String signedVote;
}

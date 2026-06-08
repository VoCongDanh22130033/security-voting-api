package com.nlu.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoteE2ERequest {
    private Long electionId;
    private String blindSignature;
    private Long roundId;
    private String blindToken;
    private Long candidateId;
    private String inviteToken;
}

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
    private Long candidateId;    // null khi dùng encryptedVote
    private String messageToken; // SHA-256(encryptedVote) khi dùng mã hóa
    private String signature;
    private String signedVote;
    private String encryptedVote; // RSA-OAEP hex — nếu có thì candidateId sẽ null
}

package com.nlu.electionservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;



@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnonymousVote {
    @Id
    private String id;
    private String electionId;
    private String blindSignature;
}

package com.nlu.electionservice.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class CandidateResponse {
  private Long id;
  private String name;
  private String description;
  private Long electionId;
  private long voteCount;
  private String imageUrl;

}
package com.nlu.electionservice.dto;

import lombok.Data;

@Data
public class CandidateRequest {
  private Long id;
  private String name;
  private String description;
  private Long electionId;
  private String imageUrl;

}
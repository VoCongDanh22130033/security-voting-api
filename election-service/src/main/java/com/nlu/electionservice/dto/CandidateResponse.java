package com.nlu.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateResponse {
  private Long id;
  private String name;
  private String description;
  private String imageUrl;
  private Long electionId;
  private Long voteCount; // Sửa từ long thành Long
}
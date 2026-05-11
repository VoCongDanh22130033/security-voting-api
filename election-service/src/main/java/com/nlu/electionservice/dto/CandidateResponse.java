package com.nlu.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder; // ✅ Thêm Builder để fix lỗi ở Controller
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateResponse {
  private Long id;
  private String name;
  private String description;
  private Long electionId;
  private String imageUrl;
}
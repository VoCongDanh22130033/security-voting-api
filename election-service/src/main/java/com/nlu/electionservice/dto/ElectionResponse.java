package com.nlu.electionservice.dto;

import java.util.List;
import lombok.Data;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Data
//trả về của cuộc bầu cử
public class ElectionResponse {
  private Long id;
  private String title;
  private String description;
  private String status;
  private LocalDateTime startDate;
  private LocalDateTime endDate;
  private Long roleId;
  private String image;
  private List<CandidateResponse> candidates;

}
package com.nlu.electionservice.dto;

import com.nlu.electionservice.entity.ElectionRound;
import java.util.List;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ElectionResponse {
  private Long id;
  private String title;
  private String description;
  private String status;
  private LocalDateTime startDate;
  private LocalDateTime endDate;
  private Long roleId;
  private String image;
  private Long winnerId;
  private Long currentRoundId;
  private Integer currentRoundNumber;
  private String currentRoundTitle;
  private String audienceType;
  private List<Long> targetDepartmentIds;
  private List<String> targetDepartmentNames;
  private List<CandidateResponse> candidates;
  private List<ElectionRound> rounds;
  private List<RoundDetailDto> roundDetails;
}

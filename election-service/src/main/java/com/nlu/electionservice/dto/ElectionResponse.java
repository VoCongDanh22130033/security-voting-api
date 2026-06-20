package com.nlu.electionservice.dto;

import com.nlu.electionservice.entity.ElectionRound;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

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
  private List<CandidateResponse> candidates;
  private List<ElectionRound> rounds;
  private List<RoundDetailDto> roundDetails;
}

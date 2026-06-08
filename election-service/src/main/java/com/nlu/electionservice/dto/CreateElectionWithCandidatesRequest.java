package com.nlu.electionservice.dto;

import com.nlu.electionservice.dto.CreateElectionRequest.NewCandidateDto;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

// Trong file CreateElectionWithCandidatesRequest.java của bạn:
@Data
public class CreateElectionWithCandidatesRequest {
  private String title;
  private String description;
  private Integer totalRounds;
  private List<RoundTimeSettingDto> roundsTimeSettings; // Mảng động nhận từ FE
  private List<Long> candidateIds;
  private List<NewCandidateDto> newCandidates;

  @Data
  public static class RoundTimeSettingDto {
    private Integer roundNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxAdvanceCount;
    private String title;
    private String description;

  }
}

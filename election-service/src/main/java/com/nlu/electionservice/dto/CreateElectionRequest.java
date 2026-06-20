package com.nlu.electionservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateElectionRequest {

  private String title;
  private String description;
  private Integer totalRounds;
  private String base64Image;
  private String imageUrl;

  private List<RoundTimeSettingDto> roundsTimeSettings;
  private List<Long> candidateIds;
  private List<NewCandidateDto> newCandidates;
  private List<UpdateCandidateDto> updatedCandidates;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RoundTimeSettingDto {
    private Integer roundNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxAdvanceCount;
    private String title;
    private String description;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class NewCandidateDto {
    private String name;
    private String party;
    private String description;
    private String base64Image;
    private String imageUrl;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class UpdateCandidateDto {
    private Long id;
    private String name;
    private String party;
    private String description;
    private String base64Image;
  }
}

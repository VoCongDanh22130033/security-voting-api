
package com.nlu.electionservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ElectionRequest {
  private Long id;
  private String title;
  private String description;
  private String status;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private LocalDateTime startTime;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private LocalDateTime endTime;

  private Long roleId;
  private List<CandidateRequest> candidates;
  private String imageUrl;

}
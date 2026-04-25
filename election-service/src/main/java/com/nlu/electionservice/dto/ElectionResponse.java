package com.nlu.electionservice.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class ElectionResponse {
  private Long id;
  private String title;
  private String description;
  private String status;
  private LocalDateTime startDate;
  private LocalDateTime endDate;
}
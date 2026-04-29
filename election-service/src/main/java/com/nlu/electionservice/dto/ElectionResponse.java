package com.nlu.electionservice.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
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


}
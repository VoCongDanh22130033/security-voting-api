package com.nlu.notificationservice.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class RealtimeNotification {
  private String type;
  private String title;
  private String message;
  private Long electionId;
  private Long roundId;
  private Integer roundNumber;
  private LocalDateTime createdAt;
  private Map<Long, Long> voteData;
}

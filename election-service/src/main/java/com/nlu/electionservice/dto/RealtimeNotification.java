package com.nlu.electionservice.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RealtimeNotification {
  private String type;
  private String title;
  private String message;
  private Long electionId;
  private Long roundId;
  private Integer roundNumber;
  private LocalDateTime createdAt;
  // candidateId -> voteCount, dùng cho VOTE_COUNT_UPDATE
  private Map<Long, Long> voteData;
}

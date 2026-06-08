package com.nlu.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundDetailDto {
    private Long id;
    private Integer roundNumber;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer maxAdvanceCount;
    private List<RoundCandidateInfo> candidates;
}

package com.nlu.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundCandidateInfo {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Long voteCount;
    private Boolean isAdvanced; // true nếu đi tiếp, false nếu bị loại, null nếu vòng chưa kết thúc
}
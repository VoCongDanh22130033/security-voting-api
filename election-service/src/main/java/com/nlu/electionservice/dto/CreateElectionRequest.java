package com.nlu.electionservice.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data // Tự động sinh Getter/Setter cho toàn bộ thuộc tính lớp cha
public class CreateElectionRequest {

  private String title;
  private String description;
  private Integer totalRounds;
  private String base64Image;
  
  // --- Thêm cấu hình đối tượng ---
  private String audienceType; // "COMPANY_WIDE" hoặc "DEPARTMENT_SPECIFIC"
  private List<Long> departmentIds; // Danh sách ID phòng ban nếu chọn DEPARTMENT_SPECIFIC
  // -------------------------------

  // Danh sách cấu hình thời gian & số người thắng của từng vòng từ FE truyền lên
  private List<RoundTimeSettingDto> roundsTimeSettings;

  private List<Long> candidateIds;

  // Danh sách ứng viên thêm nhanh thủ công
  private List<NewCandidateDto> newCandidates;

  // Danh sách ứng viên cần cập nhật
  private List<UpdateCandidateDto> updatedCandidates;

  // ====================================================================
  // 1. LỚP NỘI BỘ: CẤU HÌNH THỜI GIAN VÀ TIÊU CHÍ TỪNG VÒNG
  // ====================================================================
  @Data
  public static class RoundTimeSettingDto {
    private Integer roundNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxAdvanceCount;
    private String title;
    private String description;
  }

  // ====================================================================
  // 2. LỚP NỘI BỘ: THÔNG TIN ỨNG VIÊN ĐIỀN THỦ CÔNG
  // ====================================================================
  @Data
  public static class NewCandidateDto {
    private String name;
    private String party;
    private String description;
    private String base64Image;
  }

  // ====================================================================
  // 3. LỚP NỘI BỘ: THÔNG TIN ỨNG VIÊN CẦN CẬP NHẬT
  // ====================================================================
  @Data
  public static class UpdateCandidateDto {
    private Long id;
    private String name;
    private String party;
    private String description;
    private String base64Image;
  }
}

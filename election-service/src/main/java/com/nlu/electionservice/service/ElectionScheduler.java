package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import com.nlu.electionservice.repository.ElectionRepository;
import com.nlu.electionservice.repository.ElectionRoundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ElectionScheduler {

  @Autowired
  private ElectionRepository electionRepository;

  @Autowired
  private ElectionRoundRepository roundRepository; // BỔ SUNG: Thêm repo để tương tác với bảng vòng đấu

  /**
   * TỰ ĐỘNG MỞ CUỘC BẦU CỬ VÀ CÁC VÒNG ĐẤU ĐẾN GIỜ KHỞI TRANH
   * Tần suất: 5 giây quét một lần (fixedRate) để giao diện React cập nhật tức thì khi test
   */
  @Scheduled(fixedRate = 5000)
  @Transactional
  public void autoOpenElectionsAndRounds() {
    LocalDateTime now = LocalDateTime.now();

    // 1. Quét mở trạng thái cho Cuộc bầu cử cha (Election)
    List<Election> upcomingElections = electionRepository.findAllByStatusAndStartTimeBefore("UPCOMING", now);
    if (!upcomingElections.isEmpty()) {
      upcomingElections.forEach(election -> {
        election.setStatus("OPEN");
        log.info(">>> [SCHEDULER] Cuộc bầu cử ID: {} đã chính thức chuyển sang OPEN.", election.getId());
      });
      electionRepository.saveAll(upcomingElections);
    }

    // 2. PHÁT HIỆN LỖI TẠI ĐÂY: Quét mở trạng thái cho từng Vòng đấu con (ElectionRound)
    // Cần đảm bảo ElectionRoundRepository đã viết phương thức: findByStatusAndStartTimeBefore(String status, LocalDateTime time)
    try {
      List<ElectionRound> upcomingRounds = roundRepository.findByStatusAndStartTimeBefore("UPCOMING", now);
      if (!upcomingRounds.isEmpty()) {
        upcomingRounds.forEach(round -> {
          round.setStatus("OPEN");
          log.info(">>> [SCHEDULER] Vòng đấu số {} của Cuộc bầu cử ID: {} đã tự động chuyển sang OPEN!",
              round.getRoundNumber(), round.getElection().getId());
        });
        roundRepository.saveAll(upcomingRounds);
      }
    } catch (Exception e) {
      log.error("Lỗi khi quét mở vòng đấu: {}", e.getMessage());
    }
  }

  /**
   * TỰ ĐỘNG ĐÓNG CUỘC BẦU CỬ VÀ VÒNG ĐẤU KHI HẾT HẠN
   */
  @Scheduled(fixedRate = 5000)
  @Transactional
  public void autoCloseElectionsAndRounds() {
    LocalDateTime now = LocalDateTime.now();

    // 1. Quét đóng trạng thái cho từng Vòng đấu con (ElectionRound) trước
    try {
      List<ElectionRound> activeRounds = roundRepository.findByStatusAndEndTimeBefore("OPEN", now);
      if (!activeRounds.isEmpty()) {
        activeRounds.forEach(round -> {
          round.setStatus("CLOSED"); // Đồng bộ chuỗi trạng thái đóng
          log.info(">>> [SCHEDULER] Vòng đấu số {} của Cuộc bầu cử ID: {} đã kết thúc.",
              round.getRoundNumber(), round.getElection().getId());
        });
        roundRepository.saveAll(activeRounds);
      }
    } catch (Exception e) {
      log.error("Lỗi khi quét đóng vòng đấu: {}", e.getMessage());
    }

    // 2. Quét đóng trạng thái cho Cuộc bầu cử cha (Election)
    List<Election> expiredElections = electionRepository.findAllByStatusAndEndTimeBefore("OPEN", now);
    if (!expiredElections.isEmpty()) {
      expiredElections.forEach(election -> {
        election.setStatus("CLOSED"); // Đồng bộ đồng nhất với logic calculateStatus của hệ thống
        log.info(">>> [SCHEDULER] Cuộc bầu cử ID: {} đã được đóng tự động.", election.getId());
      });
      electionRepository.saveAll(expiredElections);
    }
  }
}
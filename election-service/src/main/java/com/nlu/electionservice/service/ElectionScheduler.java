package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.repository.ElectionRepository;
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

  @Scheduled(cron = "0 * * * * *")
  @Transactional
  public void autoCloseElections() {
    LocalDateTime now = LocalDateTime.now();
    log.info("Bắt đầu quét để đóng các cuộc bầu cử hết hạn lúc: {}", now);

    // Tìm các cuộc bầu cử đang OPEN và đã quá giờ kết thúc
    // Lưu ý: Bạn có thể viết thêm method này trong ElectionRepository
    List<Election> expiredElections = electionRepository.findAllByStatusAndEndTimeBefore(
        "OPEN", now
    );

    if (!expiredElections.isEmpty()) {
      expiredElections.forEach(election -> {
        election.setStatus("ENDED"); // Hoặc "CLOSED" tùy bạn đặt
        log.info("Cuộc bầu cử ID: {} đã được đóng tự động.", election.getId());
      });
      electionRepository.saveAll(expiredElections);
      log.info("Đã cập nhật trạng thái cho {} cuộc bầu cử.", expiredElections.size());
    }
  }
  @Scheduled(cron = "0 * * * * *")
  @Transactional
  public void autoOpenElections() {
    LocalDateTime now = LocalDateTime.now();
    List<Election> upcomingElections = electionRepository.findAllByStatusAndStartTimeBefore(
        "UPCOMING", now
    );

    upcomingElections.forEach(election -> {
      election.setStatus("OPEN");
      log.info("Cuộc bầu cử ID: {} đã chính thức bắt đầu.", election.getId());
    });
    electionRepository.saveAll(upcomingElections);
  }
}
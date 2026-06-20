package com.nlu.electionservice.service;

import com.nlu.electionservice.dto.RealtimeNotification;
import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.entity.ElectionRound;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RealtimeNotificationService {

  private final NotificationClient notificationClient;

  public RealtimeNotificationService(NotificationClient notificationClient) {
    this.notificationClient = notificationClient;
  }

  public void electionOpened(Election election) {
    send(RealtimeNotification.builder()
        .type("ELECTION_OPENED")
        .title("Cuộc bầu cử đã mở")
        .message("Cuộc bầu cử \"" + election.getTitle() + "\" đã bắt đầu.")
        .electionId(election.getId())
        .createdAt(LocalDateTime.now())
        .build());
  }

  public void roundOpened(ElectionRound round) {
    Election election = round.getElection();
    send(RealtimeNotification.builder()
        .type("ROUND_OPENED")
        .title("Vòng bầu cử đã bắt đầu")
        .message(label(round) + " của \"" + election.getTitle() + "\" đã bắt đầu.")
        .electionId(election.getId())
        .roundId(round.getId())
        .roundNumber(round.getRoundNumber())
        .createdAt(LocalDateTime.now())
        .build());
  }

  public void roundClosed(ElectionRound round) {
    Election election = round.getElection();
    send(RealtimeNotification.builder()
        .type("ROUND_CLOSED")
        .title("Vòng bầu cử đã kết thúc")
        .message(label(round) + " của \"" + election.getTitle() + "\" đã kết thúc. Kết quả đang được cập nhật.")
        .electionId(election.getId())
        .roundId(round.getId())
        .roundNumber(round.getRoundNumber())
        .createdAt(LocalDateTime.now())
        .build());
  }

  public void electionClosed(Election election) {
    send(RealtimeNotification.builder()
        .type("ELECTION_CLOSED")
        .title("Cuộc bầu cử đã kết thúc")
        .message("Cuộc bầu cử \"" + election.getTitle() + "\" đã kết thúc.")
        .electionId(election.getId())
        .createdAt(LocalDateTime.now())
        .build());
  }

  public void voteCountUpdated(Long electionId, Long roundId, List<Map<String, Object>> stats) {
    Map<Long, Long> voteData = new HashMap<>();
    for (Map<String, Object> row : stats) {
      Long candidateId = ((Number) row.get("candidateId")).longValue();
      Long count = ((Number) row.get("voteCount")).longValue();
      voteData.put(candidateId, count);
    }
    send(RealtimeNotification.builder()
        .type("VOTE_COUNT_UPDATE")
        .electionId(electionId)
        .roundId(roundId)
        .voteData(voteData)
        .createdAt(LocalDateTime.now())
        .build());
  }

  public void countdown(ElectionRound round, long minutesLeft) {
    Election election = round.getElection();
    send(RealtimeNotification.builder()
        .type("ROUND_COUNTDOWN")
        .title("Sắp hết thời gian bầu cử")
        .message("Còn " + minutesLeft + " phút nữa là kết thúc " + label(round) + " của \"" + election.getTitle() + "\".")
        .electionId(election.getId())
        .roundId(round.getId())
        .roundNumber(round.getRoundNumber())
        .createdAt(LocalDateTime.now())
        .build());
  }

  private void send(RealtimeNotification notification) {
    try {
      notificationClient.publish(notification);
    } catch (Exception ex) {
      System.err.println("Không gửi được thông báo realtime sang notification-service: " + ex.getMessage());
    }
  }

  private String label(ElectionRound round) {
    return round.getTitle() != null && !round.getTitle().isBlank()
        ? round.getTitle()
        : "Vòng " + round.getRoundNumber();
  }
}

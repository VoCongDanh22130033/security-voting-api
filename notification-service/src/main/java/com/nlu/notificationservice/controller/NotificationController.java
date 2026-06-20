package com.nlu.notificationservice.controller;

import com.nlu.notificationservice.dto.ElectionInviteEmailRequest;
import com.nlu.notificationservice.dto.RealtimeNotification;
import com.nlu.notificationservice.dto.RoundClosedEmailRequest;
import com.nlu.notificationservice.service.NotificationService;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @PostMapping("/publish")
  public ResponseEntity<?> publish(@RequestBody RealtimeNotification notification) {
    if (notification.getCreatedAt() == null) {
      notification.setCreatedAt(LocalDateTime.now());
    }
    notificationService.publish(notification);
    return ResponseEntity.ok(Map.of("message", "Notification published"));
  }

  @PostMapping("/email/election-invite")
  public ResponseEntity<?> sendElectionInvite(@RequestBody ElectionInviteEmailRequest request) {
    try {
      notificationService.sendElectionInvite(request);
      return ResponseEntity.ok(Map.of("message", "Election invite email sent successfully to " + request.getTo()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body(Map.of("message", "Failed to send election invite email to " + request.getTo()));
    }
  }

  @PostMapping("/email/round-closed")
  public ResponseEntity<?> sendRoundClosed(@RequestBody RoundClosedEmailRequest request) {
    try {
      notificationService.sendRoundClosed(request);
      return ResponseEntity.ok(Map.of("message", "Round closed email sent"));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body(Map.of("message", "Failed to send round closed email"));
    }
  }
}

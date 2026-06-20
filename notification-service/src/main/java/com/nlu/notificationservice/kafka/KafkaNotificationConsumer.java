package com.nlu.notificationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlu.notificationservice.dto.ElectionInviteEmailRequest;
import com.nlu.notificationservice.dto.RoundClosedEmailRequest;
import com.nlu.notificationservice.service.NotificationService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaNotificationConsumer {

    @Autowired
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "notification-events", groupId = "notification-group")
    public void consume(String message) {
        try {
            Map<String, String> payload = objectMapper.readValue(message, Map.class);
            String type = payload.get("type");

            if ("ELECTION_INVITE".equals(type)) {
                ElectionInviteEmailRequest request = new ElectionInviteEmailRequest();
                request.setTo(payload.get("to"));
                request.setFullName(payload.get("fullName"));
                request.setElectionTitle(payload.get("electionTitle"));
                request.setRoundTitle(payload.get("roundTitle"));
                request.setInviteLink(payload.get("inviteLink"));
                notificationService.sendElectionInvite(request);
                log.info(">>> Đã gửi email ELECTION_INVITE tới {}", request.getTo());

            } else if ("ROUND_CLOSED".equals(type)) {
                RoundClosedEmailRequest request = new RoundClosedEmailRequest();
                request.setTo(payload.get("to"));
                request.setFullName(payload.get("fullName"));
                request.setElectionTitle(payload.get("electionTitle"));
                request.setRoundTitle(payload.get("roundTitle"));
                request.setResultLink(payload.get("resultLink"));
                notificationService.sendRoundClosed(request);
                log.info(">>> Đã gửi email ROUND_CLOSED tới {}", request.getTo());

            } else {
                log.warn(">>> Kafka notification-events: unknown type={}", type);
            }
        } catch (Exception e) {
            log.error(">>> Lỗi xử lý Kafka notification-events: {}", e.getMessage());
        }
    }
}

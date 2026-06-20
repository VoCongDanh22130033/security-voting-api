package com.nlu.electionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlu.electionservice.dto.ElectionInviteEmailRequest;
import com.nlu.electionservice.dto.RealtimeNotification;
import com.nlu.electionservice.dto.RoundClosedEmailRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class NotificationClient {

  private static final String NOTIFICATION_TOPIC = "notification-events";

  private final RestTemplate restTemplate;

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public NotificationClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    this.restTemplate = new RestTemplate(factory);
  }

  @Value("${app.notification-service-url:http://localhost:8086}")
  private String notificationServiceUrl;

  @Value("${app.internal-service-token}")
  private String internalToken;

  private HttpHeaders headers() {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.set("X-Internal-Token", internalToken);
    return h;
  }

  @Async("notificationTaskExecutor")
  public void publish(RealtimeNotification notification) {
    restTemplate.postForEntity(notificationServiceUrl + "/api/notifications/publish", new HttpEntity<>(notification, headers()), Void.class);
  }

  public void sendElectionInvite(ElectionInviteEmailRequest request) {
    try {
      Map<String, String> payload = new HashMap<>();
      payload.put("type", "ELECTION_INVITE");
      payload.put("to", request.getTo());
      payload.put("fullName", request.getFullName());
      payload.put("electionTitle", request.getElectionTitle());
      payload.put("roundTitle", request.getRoundTitle());
      payload.put("inviteLink", request.getInviteLink());
      kafkaTemplate.send(NOTIFICATION_TOPIC, objectMapper.writeValueAsString(payload));
      log.info(">>> Đã gửi Kafka ELECTION_INVITE tới {}", request.getTo());
    } catch (Exception e) {
      log.error(">>> Lỗi khi gửi Kafka ELECTION_INVITE: {}", e.getMessage());
    }
  }

  public void sendRoundClosed(RoundClosedEmailRequest request) {
    try {
      Map<String, String> payload = new HashMap<>();
      payload.put("type", "ROUND_CLOSED");
      payload.put("to", request.getTo());
      payload.put("fullName", request.getFullName());
      payload.put("electionTitle", request.getElectionTitle());
      payload.put("roundTitle", request.getRoundTitle());
      payload.put("resultLink", request.getResultLink());
      kafkaTemplate.send(NOTIFICATION_TOPIC, objectMapper.writeValueAsString(payload));
      log.info(">>> Đã gửi Kafka ROUND_CLOSED tới {}", request.getTo());
    } catch (Exception e) {
      log.error(">>> Lỗi khi gửi Kafka ROUND_CLOSED: {}", e.getMessage());
    }
  }
}

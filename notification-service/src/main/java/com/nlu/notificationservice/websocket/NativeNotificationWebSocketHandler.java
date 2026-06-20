package com.nlu.notificationservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NativeNotificationWebSocketHandler extends TextWebSocketHandler {

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
  }

  public void broadcast(Object payload) {
    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (Exception ex) {
      System.err.println("Không serialize được thông báo: " + ex.getMessage());
      return;
    }
    for (WebSocketSession session : sessions) {
      if (!session.isOpen()) {
        sessions.remove(session);
        continue;
      }
      synchronized (session) {
        try {
          session.sendMessage(new TextMessage(json));
        } catch (Exception ex) {
          System.err.println("Không gửi được thông báo realtime tới " + session.getId() + ": " + ex.getMessage());
          sessions.remove(session);
        }
      }
    }
  }
}

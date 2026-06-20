package com.nlu.notificationservice.config;

import com.nlu.notificationservice.websocket.NativeNotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

  private final NativeNotificationWebSocketHandler nativeNotificationWebSocketHandler;

  @Value("${app.websocket-allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173}")
  private String[] allowedOriginPatterns;

  public WebSocketConfig(NativeNotificationWebSocketHandler nativeNotificationWebSocketHandler) {
    this.nativeNotificationWebSocketHandler = nativeNotificationWebSocketHandler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws-notifications")
        .setAllowedOriginPatterns(allowedOriginPatterns)
        .withSockJS();
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(nativeNotificationWebSocketHandler, "/ws-notifications-native")
        .setAllowedOriginPatterns(allowedOriginPatterns);
  }
}

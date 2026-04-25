package com.nlu.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class GatewayConfig {
  @Bean
  public RouteLocator routes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("auth-service", r -> r.path("/auth/**").uri("http://localhost:8081"))
        .route("voter-service", r -> r.path("/voter/**").uri("http://localhost:8082"))
        // Khớp với path /api/ mà Frontend gọi và trỏ về port 8083
        .route("election-service", r -> r.path("/api/elections/**", "/api/candidates/**")
            .uri("http://localhost:8083"))
        .build();
  }
}
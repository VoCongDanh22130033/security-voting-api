package com.nlu.apigateway.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {

    System.out.println("➡️ Request: " + exchange.getRequest().getURI());

    return chain.filter(exchange).then(
        Mono.fromRunnable(() -> {
          System.out.println("⬅️ Response: " + exchange.getResponse().getStatusCode());
        })
    );
  }
}
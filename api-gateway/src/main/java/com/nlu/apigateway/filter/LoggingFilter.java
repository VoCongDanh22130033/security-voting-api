package com.nlu.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter {

  private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

  @Value("${app.gateway-request-logging-enabled:false}")
  private boolean requestLoggingEnabled;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
    if (!requestLoggingEnabled) {
      return chain.filter(exchange);
    }

    log.info("Request: {}", exchange.getRequest().getURI());
    return chain.filter(exchange).then(
        Mono.fromRunnable(() -> log.info("Response: {}", exchange.getResponse().getStatusCode()))
    );
  }
}

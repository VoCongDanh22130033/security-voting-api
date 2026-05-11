package com.nlu.apigateway.filter;

import com.nlu.apigateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
  @Autowired
  private JwtUtil jwtUtil;

  @Override
  public int getOrder() { return 0; }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) return chain.filter(exchange);
    if (path.contains("/auth/login") || path.contains("/auth/register")|| path.contains("/auth/verify-email") || path.contains("/api/elections")) {
      return chain.filter(exchange);
    }
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
    String token = authHeader.substring(7).trim();
    try {
      String username = jwtUtil.extractUsername(token);
      ServerWebExchange mutatedExchange = exchange.mutate()
          .request(exchange.getRequest().mutate().header("X-User-Email", username).build())
          .build();
      return chain.filter(mutatedExchange);
    } catch (Exception e) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
  }
}
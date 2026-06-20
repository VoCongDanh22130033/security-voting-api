package com.nlu.apigateway.filter;

import com.nlu.apigateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  private ReactiveStringRedisTemplate redisTemplate;

  @Value("${app.internal-service-token}")
  private String internalServiceToken;

  private static final String BLACKLIST_PREFIX = "blacklist:jwt:";

  @Override
  public int getOrder() { return 0; }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) return chain.filter(exchange);
    boolean isPublic = path.contains("/auth/login")
        || path.contains("/auth/register")
        || path.contains("/auth/verify-email")
        || path.contains("/voter/forgot-password")
        || path.contains("/voter/verify-otp")
        || path.contains("/voter/reset-password-otp")
        || path.contains("/api/elections/invites/verify")
        || path.contains("/api/elections/my-elections")
        || path.contains("/api/v1/votes/cast-e2e")
        || path.contains("/api/v1/votes/submit-anonymous")
        || path.contains("/api/v1/votes/count")
        || path.contains("/api/crypto/")
        || path.contains("/notification/ws-notifications");

    if (!isPublic && exchange.getRequest().getMethod() == HttpMethod.GET
        && (path.matches(".*/api/elections/\\d+")
            || path.matches(".*/api/elections/\\d+/rounds.*")
            || path.matches(".*/api/elections/rounds/\\d+/candidates.*"))) {
      isPublic = true;
    }

    if (isPublic) {
      ServerWebExchange withToken = exchange.mutate()
          .request(exchange.getRequest().mutate()
              .header("X-Internal-Token", internalServiceToken)
              .build())
          .build();
      return chain.filter(withToken);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
    String token = authHeader.substring(7).trim();

    return redisTemplate.hasKey(BLACKLIST_PREFIX + token)
        .defaultIfEmpty(false)
        .flatMap(isBlacklisted -> {
          if (Boolean.TRUE.equals(isBlacklisted)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
          }
          try {
            String username = jwtUtil.extractUsername(token);
            String roles = jwtUtil.extractRoles(token);
            if (!isAuthorized(path, exchange.getRequest().getMethod(), roles)) {
              exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
              return exchange.getResponse().setComplete();
            }
            ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                    .header("X-User-Email", username)
                    .header("X-User-Roles", roles)
                    .header("X-Internal-Token", internalServiceToken)
                    .build())
                .build();
            return chain.filter(mutatedExchange);
          } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
          }
        });
  }

  private boolean isAuthorized(String path, HttpMethod method, String roles) {
    if (hasRole(roles, "ROLE_ADMIN")) {
      return true;
    }

    if (path.contains("/api/audit") || path.contains("/auth/admin") || path.contains("/api/v1/dashboard")) {
      return false;
    }

    boolean electionMutation = method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE;
    boolean organizerArea = path.contains("/api/rounds")
        || path.contains("/api/elections/create")
        || path.contains("/api/elections/upload")
        || path.contains("/api/elections/voter/all")
        || path.contains("/participants/")
        || path.contains("/reports/")
        || path.contains("/rounds/")
        || path.contains("/synchronize-votes")
        || (path.contains("/api/elections/") && electionMutation);

    if (organizerArea) {
      return hasRole(roles, "ROLE_ORGANIZER");
    }

    return true;
  }

  private boolean hasRole(String roles, String role) {
    return roles != null && roles.contains(role);
  }
}

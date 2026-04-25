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
  public int getOrder() {
    // Để số thấp để đảm bảo chạy sau các cấu hình hệ thống quan trọng
    return 0;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // 1. Luôn cho phép OPTIONS (CORS) đi qua
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
      return chain.filter(exchange);
    }

    // 2. Bỏ qua check JWT cho auth-service
    if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/api/elections")){
      return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    // 3. Kiểm tra Header Authorization
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      System.err.println(" Missing or invalid Authorization header for path: " + path);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }

    // Cắt token và xóa khoảng trắng thừa
    String token = authHeader.substring(7).trim();

    try {
      // 4. Giải mã Token
      String username = jwtUtil.extractUsername(token);

      // Log để chắc chắn Gateway đã đọc được user
      System.out.println(" Authorized user: " + username + " for path: " + path);

      // 5. Chèn username vào Header để các Service bên dưới sử dụng
      ServerWebExchange mutatedExchange = exchange.mutate()
          .request(exchange.getRequest().mutate()
              .header("X-User", username)
              // Bạn có thể chèn thêm Role nếu cần
              .build())
          .build();

      return chain.filter(mutatedExchange);

    } catch (Exception e) {
      System.err.println("JWT Validation failed: " + e.getMessage());
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
  }
}
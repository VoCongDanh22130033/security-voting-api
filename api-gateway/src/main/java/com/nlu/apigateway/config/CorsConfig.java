package com.nlu.apigateway.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.WebFilter;

@Configuration
public class CorsConfig {
  @Value("${app.cors-allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
  private List<String> allowedOrigins;

  @Bean
  public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedHeaders(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }
  @Bean
  public WebFilter dedupeCorsFilter() {
    return (exchange, chain) -> chain.filter(exchange).doOnSuccess(v -> {
      var headers = exchange.getResponse().getHeaders();

      // 1. Khử trùng lặp cho Access-Control-Allow-Origin
      List<String> origins = headers.get("Access-Control-Allow-Origin");
      if (origins != null && origins.size() > 1) {
        String firstOrigin = origins.get(0);
        headers.set("Access-Control-Allow-Origin", firstOrigin); // Giữ lại phần tử đầu tiên
      }

      // 2. Khử trùng lặp cho Access-Control-Allow-Credentials
      List<String> credentials = headers.get("Access-Control-Allow-Credentials");
      if (credentials != null && credentials.size() > 1) {
        String firstCred = credentials.get(0);
        headers.set("Access-Control-Allow-Credentials", firstCred);
      }
    });
  }
}

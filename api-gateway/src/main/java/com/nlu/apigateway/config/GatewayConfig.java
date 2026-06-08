package com.nlu.apigateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Autowired
    AuthenticationFilter filter;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service-public", r -> r.path("/auth/login", "/auth/register", "/auth/verify-email")
                        .filters(f -> f.stripPrefix(1))
                        .uri("http://localhost:8081"))
                .route("auth-service-secured", r -> r.path("/auth/api/**", "/auth/admin/**", "/auth/logout")
                        .filters(f -> f.stripPrefix(1).filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8081"))
                .route("audit-service", r -> r.path("/api/audit/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8085"))
                .route("voter-service", r -> r.path("/voter/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8082"))
                .route("vote-service", r -> r.path("/api/votes/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8083"))
                .route("election-service-prefixed", r -> r.path("/election/**")
                        .filters(f -> f.stripPrefix(1).filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8083"))
                .route("election-vote-service", r -> r.path("/api/v1/votes/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8083"))
                .route("election-service", r -> r.path("/api/elections/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8083"))
                .route("crypto-service", r -> r.path("/api/crypto/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri("http://localhost:8084"))
                .build();
    }
}

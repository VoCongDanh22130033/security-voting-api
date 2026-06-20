package com.nlu.apigateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Autowired
    AuthenticationFilter filter;

    @Value("${services.auth-url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${services.voter-url:http://localhost:8082}")
    private String voterServiceUrl;

    @Value("${services.election-url:http://localhost:8083}")
    private String electionServiceUrl;

    @Value("${services.crypto-url:http://localhost:8084}")
    private String cryptoServiceUrl;

    @Value("${services.audit-url:http://localhost:8085}")
    private String auditServiceUrl;

    @Value("${services.notification-url:http://localhost:8086}")
    private String notificationServiceUrl;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service-public", r -> r.path("/auth/login", "/auth/register", "/auth/verify-email")
                        .filters(f -> f.stripPrefix(1))
                        .uri(authServiceUrl))
                .route("auth-service-secured", r -> r.path("/auth/api/**", "/auth/admin/**", "/auth/logout")
                        .filters(f -> f.stripPrefix(1).filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri(authServiceUrl))
                .route("audit-service", r -> r.path("/api/audit/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri(auditServiceUrl))
                .route("voter-service", r -> r.path("/voter/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri(voterServiceUrl))
                .route("vote-service", r -> r.path("/api/votes/**")
                        .uri(electionServiceUrl))
                .route("notification-websocket", r -> r.path(
                                "/notification/ws-notifications",
                                "/notification/ws-notifications/**",
                                "/notification/ws-notifications-native",
                                "/notification/ws-notifications-native/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri(notificationServiceUrl))
                .route("notification-service", r -> r.path("/api/notifications/**")
                        .uri(notificationServiceUrl))
                .route("election-service-prefixed", r -> r.path("/election/**")
                        .filters(f -> f.stripPrefix(1).filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri(electionServiceUrl))
                .route("election-vote-service", r -> r.path("/api/v1/votes/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri(electionServiceUrl))
                .route("election-service", r -> r.path("/api/elections/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri(electionServiceUrl))
                .route("rounds-service", r -> r.path("/api/rounds/**")
                        .filters(f -> f.filter(filter.apply(new AuthenticationFilter.Config())))
                        .uri(electionServiceUrl))
                .route("crypto-service", r -> r.path("/api/crypto/**")
                        .uri(cryptoServiceUrl))
                .build();
    }
}

package com.nlu.apigateway.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/verify-email",
            "/voter/forgot-password",
            "/voter/verify-otp",
            "/voter/reset-password-otp",
            "/api/elections/invites/verify",
            "/api/elections/my-elections",
            "/api/v1/votes/cast-e2e",
            "/notification/ws-notifications",
            "/notification/ws-notifications-native",
            "/api/crypto/public-key",
            "/api/crypto/vote-encryption-key",
            "/api/crypto/sign",
            "/api/v1/votes/submit-anonymous",
            "/api/v1/votes/count",
            "/eureka"
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> openApiEndpoints
                    .stream()
                    .noneMatch(uri -> request.getURI().getPath().contains(uri));

}
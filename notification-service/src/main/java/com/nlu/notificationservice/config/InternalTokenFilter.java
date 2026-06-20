package com.nlu.notificationservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    @Value("${app.internal-service-token}")
    private String expectedToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // WebSocket upgrade and actuator bypass token check
        if (path.startsWith("/actuator") || path.contains("/ws-notifications")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = request.getHeader("X-Internal-Token");
        if (expectedToken == null || !expectedToken.equals(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

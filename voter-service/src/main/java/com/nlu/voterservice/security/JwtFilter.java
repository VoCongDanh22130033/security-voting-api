package com.nlu.voterservice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtFilter implements Filter {
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    String user = req.getHeader("X-User");

    log.debug("DEBUG - Voter-Service nhan X-User: {}", user);

    if (user == null) {
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing identity");
      return;
    }
    chain.doFilter(request, response);
  }}
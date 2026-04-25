package com.nlu.voterservice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtFilter implements Filter {
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    String user = req.getHeader("X-User");

    System.out.println("DEBUG - Voter-Service nhận X-User: " + user); // Thêm dòng này

    if (user == null) {
      // Đừng throw exception thô, hãy trả về mã lỗi 401
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing identity");
      return;
    }
    chain.doFilter(request, response);
  }}
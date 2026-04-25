package com.nlu.voterservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable()) // Tắt CSRF vì đây là API stateless
        .authorizeHttpRequests(auth -> auth
            .anyRequest().permitAll() // Cho phép tất cả request đi vào Controller
        )
        .formLogin(form -> form.disable()) // Tắt giao diện login
        .httpBasic(basic -> basic.disable()); // Tắt xác thực basic (ngăn lỗi 401 mặc định)

    return http.build();
  }
}
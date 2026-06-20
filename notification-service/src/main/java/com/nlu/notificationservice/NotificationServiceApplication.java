package com.nlu.notificationservice;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {
  @jakarta.annotation.PostConstruct
  public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
  }

  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}

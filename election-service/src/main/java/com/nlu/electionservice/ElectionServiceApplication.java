package com.nlu.electionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//thời gian đóng mở cuộc bầu cử
@EnableScheduling
public class ElectionServiceApplication {
  @jakarta.annotation.PostConstruct
  public void init() {
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
  }
  public static void main(String[] args) {
    SpringApplication.run(ElectionServiceApplication.class, args);
  }

}

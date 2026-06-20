package com.nlu.electionservice.controller;

import com.nlu.electionservice.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Value("${app.internal-service-token}")
    private String internalToken;

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(dashboardService.getStatistics());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        // Danh sách service và endpoint kiểm tra (ưu tiên actuator/health, fallback endpoint thực)
        String[][] serviceList = {
            {"auth-service",         "http://localhost:8081/actuator/health"},
            {"voter-service",        "http://localhost:8082/actuator/health/ping"},
            {"election-service",     "http://localhost:8083/actuator/health"},
            {"crypto-service",       "http://localhost:8084/actuator/health"},
            {"audit-service",        "http://localhost:8085/actuator/health"},
            {"notification-service", "http://localhost:8086/actuator/health"},
            {"api-gateway",          "http://localhost:8080/actuator/health"}
        };

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        RestTemplate rt = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        List<Map<String, Object>> services = new ArrayList<>();
        for (String[] svc : serviceList) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", svc[0]);
            long start = System.currentTimeMillis();
            try {
                rt.exchange(svc[1], HttpMethod.GET, entity, String.class);
                info.put("status", "UP");
                info.put("responseTime", System.currentTimeMillis() - start);
            } catch (org.springframework.web.client.HttpClientErrorException ex) {
                // 4xx từ service → service đang chạy, chỉ endpoint bị chặn
                info.put("status", "UP");
                info.put("responseTime", System.currentTimeMillis() - start);
            } catch (Exception e) {
                info.put("status", "DOWN");
                info.put("responseTime", System.currentTimeMillis() - start);
            }
            services.add(info);
        }

        return ResponseEntity.ok(Map.of("services", services));
    }
}

package com.smartenergy.backend.agent;

import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.vo.ForecastPointVO;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python 能耗预测微服务客户端 (Epic 6-3)。用 WebClient 调用 FastAPI /forecast。
 * 任何调用失败都返回空列表降级，不阻断大屏主流程。
 */
@Slf4j
@Component
public class PredictionClient {

    @Value("${prediction.service.base-url}")
    private String baseUrl;
    @Value("${prediction.service.timeout-ms}")
    private long timeoutMs;

    private WebClient webClient;

    @PostConstruct
    void init() {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /** 用一段历史读数请求未来 +15/+30min 预测；失败返回空列表。 */
    public List<ForecastPointVO> forecast(String deviceCode, List<SensorData> history) {
        Map<String, Object> body = buildRequest(deviceCode, history);
        try {
            ForecastResponse resp = webClient.post()
                    .uri("/forecast")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(ForecastResponse.class)
                    .block(Duration.ofMillis(timeoutMs));
            return resp == null || resp.getForecasts() == null ? List.of() : resp.getForecasts();
        } catch (Exception e) {
            log.warn("预测服务调用失败 device={}: {}", deviceCode, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> buildRequest(String deviceCode, List<SensorData> history) {
        List<Map<String, Object>> readings = new ArrayList<>(history.size());
        for (SensorData d : history) {
            Map<String, Object> r = new HashMap<>();
            r.put("usageKwh", d.getUsageKwh());
            r.put("co2Emission", d.getCo2Emission());
            r.put("nsm", d.getNsm());
            r.put("dayOfWeek", d.getDayOfWeek());
            r.put("weekStatus", d.getWeekStatus());
            readings.add(r);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("deviceCode", deviceCode);
        body.put("history", readings);
        return body;
    }

    @Data
    static class ForecastResponse {
        private String deviceCode;
        private List<ForecastPointVO> forecasts;
    }
}

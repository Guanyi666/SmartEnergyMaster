package com.smartenergy.backend.agent;

import com.smartenergy.backend.cache.CacheKeys;
import com.smartenergy.backend.cache.CacheService;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.service.SensorDataService;
import com.smartenergy.backend.vo.ForecastPointVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 预测编排 (Epic 6-3)：Redis 读穿透(prediction:forecast:{code} TTL5min)
 * → 取最近 N 条读数 → 调 Python 服务 → 缓存。失败不缓存、返回空列表降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final SensorDataService sensorDataService;
    private final PredictionClient predictionClient;
    private final CacheService cacheService;

    @Value("${prediction.service.lookback:96}")
    private int lookback;

    public List<ForecastPointVO> getForecast(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return List.of();
        }
        List<ForecastPointVO> cached = cacheService.get(CacheKeys.forecast(deviceCode));
        if (cached != null) {
            return cached;
        }
        List<ForecastPointVO> result = loadForecast(deviceCode);
        // 仅缓存成功结果，避免把"服务临时不可用"的空结果固化 5 分钟
        if (result != null && !result.isEmpty()) {
            cacheService.set(CacheKeys.forecast(deviceCode), result, CacheKeys.FORECAST_TTL);
        }
        return result == null ? List.of() : result;
    }

    private List<ForecastPointVO> loadForecast(String deviceCode) {
        List<SensorData> history = sensorDataService.getRecentData(deviceCode, lookback);
        if (history.isEmpty()) {
            return List.of();
        }
        return predictionClient.forecast(deviceCode, history);
    }

    /**
     * 批量预热多设备预测 (Epic 11-4 异步池的实际使用方)。
     * 用独立线程池后台跑，不占用 Web 容器线程。
     */
    @Async("taskExecutor")
    public void warmUpAsync(List<String> deviceCodes) {
        for (String code : deviceCodes) {
            try {
                getForecast(code);
            } catch (Exception e) {
                log.warn("预测预热失败 device={}: {}", code, e.getMessage());
            }
        }
    }
}

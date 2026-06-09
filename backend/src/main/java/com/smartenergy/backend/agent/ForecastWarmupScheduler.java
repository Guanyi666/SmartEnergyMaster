package com.smartenergy.backend.agent;

import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.vo.DeviceOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预测缓存定时预热 (Epic 6-3 + 11-4)。
 * 每隔 interval 把全部设备的预测在后台线程池(@Async)算好写入 Redis，
 * 间隔略小于 5min 缓存 TTL，保证大屏读取时基本恒命中、不触发同步推理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastWarmupScheduler {

    private final DeviceService deviceService;
    private final ForecastService forecastService;

    @Scheduled(fixedDelayString = "${prediction.warmup.interval-ms:240000}", initialDelay = 20000)
    public void warmUp() {
        List<String> codes = deviceService.listDevices(1, 10000, null, null, null).getRecords().stream()
                .map(DeviceOverviewVO::getDeviceCode)
                .toList();
        if (!codes.isEmpty()) {
            log.debug("触发预测缓存预热, 设备数={}", codes.size());
            forecastService.warmUpAsync(codes);   // 异步线程池执行
        }
    }
}

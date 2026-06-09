package com.smartenergy.backend.cache;

import java.time.Duration;

/**
 * 缓存 key 与 TTL 统一定义 (Epic 11-2-2)，避免散落在各 Service 里拼错前缀。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** 设备最新工况：device:latest:{deviceCode}，TTL 30s，上报时主动失效。 */
    public static final String DEVICE_LATEST_PREFIX = "device:latest:";
    public static final Duration DEVICE_LATEST_TTL = Duration.ofSeconds(30);

    /** 设备总览列表：device:list，TTL 5s，设备增删改/状态变更时失效。 */
    public static final String DEVICE_LIST = "device:list";
    public static final Duration DEVICE_LIST_TTL = Duration.ofSeconds(5);

    /** 大屏汇总：dashboard:summary:{deviceCode}，TTL 5s（与前端 5s 轮询对齐），仅靠 TTL 兜底。 */
    public static final String DASHBOARD_SUMMARY_PREFIX = "dashboard:summary:";
    public static final Duration DASHBOARD_SUMMARY_TTL = Duration.ofSeconds(5);

    /** 能耗预测：prediction:forecast:{deviceCode}，TTL 5min（预测开销大，命中率优先）。 */
    public static final String FORECAST_PREFIX = "prediction:forecast:";
    public static final Duration FORECAST_TTL = Duration.ofMinutes(5);

    public static String deviceLatest(String deviceCode) {
        return DEVICE_LATEST_PREFIX + deviceCode;
    }

    public static String forecast(String deviceCode) {
        return FORECAST_PREFIX + deviceCode;
    }

    public static String dashboardSummary(String deviceCode) {
        return DASHBOARD_SUMMARY_PREFIX + (deviceCode == null || deviceCode.isBlank() ? "_default" : deviceCode);
    }
}

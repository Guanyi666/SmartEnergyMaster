package com.smartenergy.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单步能耗预测点 (Epic 6-3)。mean/lower/upper 均为 kWh（90min 平滑负荷尺度）。
 * 同时用于：接收 Python 服务响应 + 缓存 + 返回前端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPointVO {
    private int minutesAhead;   // 未来多少分钟（15 / 30）
    private double mean;        // 预测均值
    private double lower;       // 95% 置信下界（已 clamp ≥ 0）
    private double upper;       // 95% 置信上界
}

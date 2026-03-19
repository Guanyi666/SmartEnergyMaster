package com.smartenergy.backend.entity;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/18
 */

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 传感器时序数据类
 */
@Data
@TableName("sensor_data")
public class SensorData {
    //时序表没有常规的自增 ID 主键,时间戳(time)就是它的核心索引切片键
    private OffsetDateTime time;

    private Integer deviceId;      // 设备ID

    // --- 1. UCI 数据集核心特征 ---
    private BigDecimal usageKwh;         // 工业能耗/有功功率 (Usage_kWh)
    private BigDecimal co2Emission;      // 二氧化碳排放量 (tCO2)
    private Integer nsm;                 // 午夜起算秒数 (NSM)
    private Integer weekStatus;          // 周状态: 0=周末, 1=工作日
    private String dayOfWeek;            // 星期几 (如: Monday)
    private String loadType;             // 加载类型: Light_Load, Medium_Load, Maximum_Load

    // --- 2. 结合西安市 2025 政策的 AI 派生字段 ---
    private String xianPriceTier;        // 电价区间: CRITICAL_PEAK(尖峰), PEAK(高峰), FLAT(平段), VALLEY(低谷), DEEP_VALLEY(深谷)

    // --- 3. 业务拓展物理字段 (用于调度约束与根因诊断) ---
    private BigDecimal temperature;      // 核心设备温度 (℃)
    private BigDecimal vibration;        // 振动幅度 (mm/s)
    private BigDecimal pressure;         // 管道压力 (kPa)
    private Integer operatingStatus;     // 运行工况: 0=停机, 1=空转, 2=正常, 3=高负载
}

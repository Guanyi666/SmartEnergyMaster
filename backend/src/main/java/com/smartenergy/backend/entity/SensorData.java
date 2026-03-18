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

/**
 * 传感器时序数据类
 */
@Data
@TableName("sensor_data")
public class SensorData {
    //时序表没有常规的自增 ID 主键,时间戳(time)就是它的核心索引切片键
    private LocalDateTime time;

    private Integer deviceId;

    private BigDecimal temperature;

    private BigDecimal pressure;

    private BigDecimal powerConsumption;

    private BigDecimal gasFlow;

    private BigDecimal waterFlow;
}

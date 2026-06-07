package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("sensor_data")
public class SensorData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private OffsetDateTime time;

    private Integer deviceId;

    private BigDecimal usageKwh;

    private BigDecimal co2Emission;

    private Integer nsm;

    private Integer weekStatus;

    private String dayOfWeek;

    private String loadType;

    private String xianPriceTier;

    private BigDecimal temperature;

    private BigDecimal vibration;

    private BigDecimal pressure;

    private Integer operatingStatus;
}

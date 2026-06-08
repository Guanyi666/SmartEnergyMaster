package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("sensor_data")
@Schema(description = "传感器数据实体（TimescaleDB 超表）")
public class SensorData {

    @TableId(type = IdType.AUTO)
    @Schema(description = "数据 ID（自增主键）")
    private Long id;

    @Schema(description = "数据采集时间")
    private OffsetDateTime time;

    @Schema(description = "关联设备 ID")
    private Integer deviceId;

    @Schema(description = "有功功率（kWh）")
    private BigDecimal usageKwh;

    @Schema(description = "CO₂排放量（kg）")
    private BigDecimal co2Emission;

    @Schema(description = "秒级时间编码（NSM，0-86400）")
    private Integer nsm;

    @Schema(description = "周状态（0=工作日，1=周末）")
    private Integer weekStatus;

    @Schema(description = "星期几")
    private String dayOfWeek;

    @Schema(description = "负载类型（Light_Load / Medium_Load / Maximum_Load）")
    private String loadType;

    @Schema(description = "西安电价时段")
    private String xianPriceTier;

    @Schema(description = "温度（℃）")
    private BigDecimal temperature;

    @Schema(description = "振动（mm/s）")
    private BigDecimal vibration;

    @Schema(description = "压力（kPa）")
    private BigDecimal pressure;

    @Schema(description = "运行状态（0=停机，1=空转，2=运行中，3=高负荷）")
    private Integer operatingStatus;
}

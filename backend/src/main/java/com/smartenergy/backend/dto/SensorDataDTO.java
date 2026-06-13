package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 传感器数据上报请求.
 * ★ NM6 修复 (2026-06-13): 所有字符串字段补 @Size(max=) 防止超长字段入库
 */
@Data
@Schema(description = "传感器数据上报请求")
public class SensorDataDTO {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码不能超过 64 个字符")
    @Schema(description = "设备编码", example = "EAF-01")
    private String deviceCode;

    @NotNull(message = "能耗数据不能为空")
    @Schema(description = "有功功率（kWh）", example = "350.5")
    private BigDecimal usageKwh;

    @Schema(description = "CO₂排放量（kg）", example = "12.3")
    private BigDecimal co2Emission;

    @Schema(description = "秒级时间编码（NSM）", example = "45000")
    private Integer nsm;

    @Schema(description = "周状态（0=工作日，1=周末）", example = "0")
    private Integer weekStatus;

    @Size(max = 16, message = "星期字段不能超过 16 个字符")
    @Schema(description = "星期几", example = "Monday")
    private String dayOfWeek;

    @Size(max = 32, message = "负载类型不能超过 32 个字符")
    @Schema(description = "负载类型", example = "Light_Load")
    private String loadType;

    @Size(max = 32, message = "电价时段不能超过 32 个字符")
    @Schema(description = "西安电价时段（尖峰/峰/平/谷/深谷）", example = "平")
    private String xianPriceTier;

    @Schema(description = "温度（℃）", example = "850.2")
    private BigDecimal temperature;

    @Schema(description = "振动（mm/s）", example = "4.5")
    private BigDecimal vibration;

    @Schema(description = "压力（kPa）", example = "120.0")
    private BigDecimal pressure;

    @Schema(description = "运行状态（0=停机，1=空转，2=运行中，3=高负荷）", example = "2")
    private Integer operatingStatus;

    @Schema(description = "数据采集时间", example = "2026-03-18T10:30:00+08:00")
    private OffsetDateTime time;
}

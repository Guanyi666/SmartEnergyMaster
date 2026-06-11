package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@Schema(description = "维修工单视图")
public class WorkOrderVO {

    @Schema(description = "工单 ID")
    private Long id;

    @Schema(description = "工单编号")
    private String orderNo;

    @Schema(description = "设备 ID")
    private Integer deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "故障类型（MECHANICAL_JAM / COOLING_INTERRUPT 等）")
    private String faultType;

    @Schema(description = "故障描述")
    private String description;

    @Schema(description = "工单状态（PENDING / IN_PROGRESS / RESOLVED）")
    private String status;

    @Schema(description = "优先级（HIGH / CRITICAL）")
    private String priority;

    @Schema(description = "处理人")
    private String assignee;

    @Schema(description = "工单来源：AUTO（故障自动生成） / MANUAL（操作员手动创建）")
    private String source;

    @Schema(description = "故障源数据时间")
    private OffsetDateTime sourceTime;

    @Schema(description = "接受处理时间")
    private LocalDateTime acceptedAt;

    @Schema(description = "解决时间")
    private LocalDateTime resolvedAt;

    @Schema(description = "触发时温度（°C）")
    private BigDecimal latestTemperature;

    @Schema(description = "触发时振动（mm/s）")
    private BigDecimal latestVibration;

    @Schema(description = "触发时压力（kPa）")
    private BigDecimal latestPressure;

    @Schema(description = "关联 SOP 编号（自动匹配）")
    private Long sopId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
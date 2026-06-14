package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@TableName("work_order")
@Schema(description = "维修工单实体")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    @Schema(description = "工单 ID（自增主键）")
    private Long id;

    @Schema(description = "工单编号")
    private String orderNo;

    @Schema(description = "关联设备 ID")
    private Integer deviceId;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "故障类型")
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

    /** 故障触发时刻的传感器快照。完整时序在 sensor_data 表，本字段仅用于工单详情快速展示。 */
    @Schema(description = "触发时温度（°C）")
    private BigDecimal latestTemperature;

    /** 故障触发时刻的传感器快照。完整时序在 sensor_data 表，本字段仅用于工单详情快速展示。 */
    @Schema(description = "触发时振动（mm/s）")
    private BigDecimal latestVibration;

    /** 故障触发时刻的传感器快照。完整时序在 sensor_data 表，本字段仅用于工单详情快速展示。 */
    @Schema(description = "触发时压力（kPa）")
    private BigDecimal latestPressure;

    @Schema(description = "关联 SOP 编号（自动匹配）")
    private Long sopId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
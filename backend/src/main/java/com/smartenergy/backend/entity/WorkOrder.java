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

    @Schema(description = "故障源数据时间")
    private OffsetDateTime sourceTime;

    @Schema(description = "接受处理时间")
    private LocalDateTime acceptedAt;

    @Schema(description = "解决时间")
    private LocalDateTime resolvedAt;

    @Schema(description = "触发时温度（℃）")
    private BigDecimal latestTemperature;

    @Schema(description = "触发时振动（mm/s）")
    private BigDecimal latestVibration;

    @Schema(description = "触发时压力（kPa）")
    private BigDecimal latestPressure;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}

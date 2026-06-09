package com.smartenergy.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 现有 work_order 表的只读映射（新模块不修改这张表）
 * 字段名 1:1 对应 init.sql 中 work_order 的列
 */
@Data
@TableName("work_order")
@Schema(description = "现有工单（只读）")
public class ExistingWorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("device_id")
    private Integer deviceId;

    private String title;

    @TableField("fault_type")
    private String faultType;

    private String description;

    private String status;

    private String priority;

    private String assignee;

    @TableField("source_time")
    private OffsetDateTime sourceTime;

    @TableField("accepted_at")
    private LocalDateTime acceptedAt;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("latest_temperature")
    private BigDecimal latestTemperature;

    @TableField("latest_vibration")
    private BigDecimal latestVibration;

    @TableField("latest_pressure")
    private BigDecimal latestPressure;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Integer deviceId;

    private String title;

    private String faultType;

    private String description;

    private String status;

    private String priority;

    private String assignee;

    private OffsetDateTime sourceTime;

    private LocalDateTime acceptedAt;

    private LocalDateTime resolvedAt;

    private BigDecimal latestTemperature;

    private BigDecimal latestVibration;

    private BigDecimal latestPressure;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

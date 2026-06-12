package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workorder_assignment")
@Schema(description = "工单-人员指派关系（新模块事实表）")
public class WorkOrderAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("work_order_id")
    private Long workOrderId;

    @TableField("personnel_id")
    private Long personnelId;

    private String role;

    @TableField("assigned_at")
    private LocalDateTime assignedAt;

    @TableField("released_at")
    private LocalDateTime releasedAt;

    private String note;
}

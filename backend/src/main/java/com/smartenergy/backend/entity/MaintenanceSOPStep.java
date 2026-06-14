package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SOP 步骤子表实体（v4 改动组 4：从 maintenance_sop.steps JSON 数组拆分而来）。
 * 一条记录 = 一个步骤。
 */
@Data
@TableName("maintenance_sop_step")
@Schema(description = "SOP 步骤子表实体")
public class MaintenanceSOPStep {

    @TableId(type = IdType.AUTO)
    @Schema(description = "步骤 ID")
    private Long id;

    @Schema(description = "所属 SOP ID")
    private Long sopId;

    @Schema(description = "步骤序号", example = "1")
    private Integer stepNo;

    @Schema(description = "步骤标题")
    private String title;

    @Schema(description = "步骤内容")
    private String content;

    @Schema(description = "预计耗时（分钟）")
    private Integer expectedMinutes;

    @Schema(description = "注意事项")
    private String warning;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}

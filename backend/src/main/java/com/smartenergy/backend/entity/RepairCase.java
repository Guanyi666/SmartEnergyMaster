package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_case")
@Schema(description = "维修案例实体")
public class RepairCase {

    @TableId(type = IdType.AUTO)
    @Schema(description = "案例 ID")
    private Long id;

    @Schema(description = "案例编号（业务唯一）")
    private String caseCode;

    @Schema(description = "案例标题")
    private String title;

    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "故障类型")
    private String faultType;

    @Schema(description = "故障现象")
    private String faultSymptom;

    @Schema(description = "根因分析")
    private String rootCause;

    @Schema(description = "维修过程")
    private String repairProcess;

    @Schema(description = "维修结果")
    private String repairResult;

    @Schema(description = "维修耗时（分钟）")
    private Integer durationMinutes;

    @Schema(description = "维修人员")
    private String technician;

    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @Schema(description = "关联工单 ID")
    private Long relatedWorkOrderId;

    @Schema(description = "发生时间")
    private LocalDateTime occurredAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}

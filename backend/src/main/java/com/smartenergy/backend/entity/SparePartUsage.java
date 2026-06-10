package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spare_part_usage")
@Schema(description = "备件领用记录实体")
public class SparePartUsage {

    @TableId(type = IdType.AUTO)
    @Schema(description = "领用记录 ID")
    private Long id;

    @Schema(description = "备件 ID")
    private Long partId;

    @Schema(description = "关联工单 ID（可空）")
    private Long workOrderId;

    @Schema(description = "领用数量")
    private Integer quantity;

    @Schema(description = "领用人")
    private String userName;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "领用时间")
    private LocalDateTime usedAt;

    @Schema(description = "记录创建时间")
    private LocalDateTime createdAt;
}
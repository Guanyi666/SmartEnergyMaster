package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "备件领用记录视图")
public class SparePartUsageVO {

    @Schema(description = "领用记录 ID")
    private Long id;

    @Schema(description = "备件 ID")
    private Long partId;

    @Schema(description = "备件编号")
    private String partCode;

    @Schema(description = "备件名称")
    private String partName;

    @Schema(description = "关联工单 ID")
    private Long workOrderId;

    @Schema(description = "工单编号")
    private String workOrderNo;

    @Schema(description = "领用数量")
    private Integer quantity;

    @Schema(description = "领用人")
    private String userName;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "领用时间")
    private LocalDateTime usedAt;
}
package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "备件库存视图（带预警标记）")
public class SparePartVO {

    @Schema(description = "备件 ID")
    private Long id;

    @Schema(description = "备件编号")
    private String partCode;

    @Schema(description = "备件名称")
    private String name;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "计量单位")
    private String unit;

    @Schema(description = "当前库存数量")
    private Integer quantity;

    @Schema(description = "安全库存阈值")
    private Integer safetyStock;

    @Schema(description = "单价（元）")
    private BigDecimal unitPrice;

    @Schema(description = "供应商")
    private String supplier;

    @Schema(description = "存放位置")
    private String location;

    @Schema(description = "是否低于安全库存")
    private Boolean lowStock;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
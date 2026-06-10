package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "创建/更新备件请求")
public class SparePartCreateRequest {

    @NotBlank(message = "备件编号不能为空")
    @Schema(description = "备件编号")
    private String partCode;

    @NotBlank(message = "备件名称不能为空")
    @Schema(description = "备件名称")
    private String name;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "计量单位")
    private String unit;

    @PositiveOrZero(message = "库存数量不能为负")
    @Schema(description = "当前库存数量")
    private Integer quantity;

    @PositiveOrZero(message = "安全库存不能为负")
    @Schema(description = "安全库存阈值")
    private Integer safetyStock;

    @Schema(description = "单价（元）")
    private BigDecimal unitPrice;

    @Schema(description = "供应商")
    private String supplier;

    @Schema(description = "存放位置")
    private String location;
}
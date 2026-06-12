package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("spare_part")
@Schema(description = "备件库存实体")
public class SparePart {

    @TableId(type = IdType.AUTO)
    @Schema(description = "备件 ID")
    private Long id;

    @Schema(description = "备件编号（业务唯一）")
    private String partCode;

    @Schema(description = "备件名称")
    private String name;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "计量单位")
    private String unit;

    @Schema(description = "当前库存数量")
    private Integer quantity;

    @Schema(description = "安全库存阈值，低于此值触发红色预警")
    private Integer safetyStock;

    @Schema(description = "单价（元）")
    private BigDecimal unitPrice;

    @Schema(description = "供应商")
    private String supplier;

    @Schema(description = "存放位置")
    private String location;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
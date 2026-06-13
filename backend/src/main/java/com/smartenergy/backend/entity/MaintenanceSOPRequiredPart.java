package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SOP 所需备件子表实体（v4 改动组 4：从 maintenance_sop.required_parts JSON 数组拆分而来）。
 * 一条记录 = 一个所需备件项。
 */
@Data
@TableName("maintenance_sop_required_part")
@Schema(description = "SOP 所需备件子表实体")
public class MaintenanceSOPRequiredPart {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "所属 SOP ID")
    private Long sopId;

    @Schema(description = "关联 spare_part.id（JOIN spare_part 可取详细库存信息）")
    private Long partId;

    @Schema(description = "备件编码（v4 用 part_code 替代 part_name，因为 JSON 数组元素就是 part_code）", example = "BRG-EAF-001")
    private String partCode;

    @Schema(description = "所需数量")
    private Integer quantity;

    @Schema(description = "是否必需")
    private Boolean isMandatory;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}

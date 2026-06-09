package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通用分页查询参数")
public class PageQuery {

    @Schema(description = "页码（从 1 开始）", example = "1", defaultValue = "1")
    private Long pageNum = 1L;

    @Schema(description = "每页条数", example = "20", defaultValue = "20")
    private Long pageSize = 20L;
}

package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "调度建议")
public class DispatchAdviceVO {

    @Schema(description = "建议等级（CRITICAL / WARN / GOOD / INFO）", example = "WARN")
    private String level;

    @Schema(description = "建议标题", example = "峰时电价预警")
    private String title;

    @Schema(description = "建议详情", example = "当前处于峰电价时段且负荷偏高，建议推迟非关键排产至谷时段")
    private String content;
}

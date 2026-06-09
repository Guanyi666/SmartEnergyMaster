package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 调度建议 (Epic 6-5)。在 level/title/content 基础上，
 * 升级出可执行的 suggestedAction（建议动作）与 estimatedSaving（预估收益），供大屏确认/拒绝。
 */
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

    @Schema(description = "一句话可执行动作", example = "将非关键负荷顺延 30-60 分钟避峰")
    private String suggestedAction;

    @Schema(description = "预估收益/成本影响", example = "预计避峰节省 ≈ ¥80/小时")
    private String estimatedSaving;

    /** 兼容旧三参构造：无动作/收益时使用。 */
    public DispatchAdviceVO(String level, String title, String content) {
        this(level, title, content, null, "—");
    }
}

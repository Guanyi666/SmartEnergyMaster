package com.smartenergy.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单状态更新请求.
 *
 * ★ H2 + Bug-NEW-2 修复 (2026-06-13):
 * 旧版 assigneeProvided 永远 false (旧注释说"由 controller 从 Map 里读"但
 * 实际 Controller 收的是 typed DTO 不是 Map → 死注释 + 死字段 → API 永远无法清空 assignee).
 *
 * 修复后用自定义 setter 模式: Jackson 反序列化 JSON 时,只要 "assignee" 字段出现
 * 在请求体里 (无论值是 null 还是字符串), setAssignee 都会被调用,从而把
 * assigneeProvided 置为 true。WorkOrderServiceImpl.updateStatus 借此判断
 * "用户是否想清空 assignee" 而不是 "用户没传这个字段"。
 */
@Schema(description = "工单状态更新请求")
public class WorkOrderStatusRequest {

    @Getter @Setter
    @NotBlank(message = "工单状态不能为空")
    @Schema(description = "目标状态（PENDING / IN_PROGRESS / RESOLVED）", example = "IN_PROGRESS")
    private String status;

    @Getter
    @Schema(description = "处理人 (传 null 即可清空)", example = "张三")
    private String assignee;

    @Getter @Setter
    @Schema(description = "处理备注", example = "已确认故障，正在安排维修")
    private String note;

    /**
     * ★ H2 修复: 由自定义 setAssignee 自动置位,客户端 / 测试代码不应直接修改.
     */
    @JsonIgnore
    @Getter
    private boolean assigneeProvided = false;

    /**
     * ★ H2 关键 setter: Jackson 反序列化时只要 "assignee" 在 JSON 中出现就调用,
     * 让我们能区分 "传了 null 想清空" vs "没传这个字段".
     */
    public void setAssignee(String assignee) {
        this.assignee = assignee;
        this.assigneeProvided = true;
    }
}

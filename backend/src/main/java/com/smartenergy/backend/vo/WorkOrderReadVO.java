package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单只读响应 VO
 * 包含 work_order 全字段 + device JOIN + assigneeName JOIN
 * （🟠 脏写风险修正：assigneeName 来自 workorder_assignment JOIN，不读 work_order.assignee 列）
 */
@Data
@Schema(description = "工单只读视图（含指派人姓名 JOIN）")
public class WorkOrderReadVO {

    // ---- work_order 原字段 ----
    @Schema(description = "工单 ID")
    private Long id;

    @Schema(description = "工单编号")
    private String orderNo;

    @Schema(description = "设备 ID")
    private Integer deviceId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "故障类型英文常量：MECHANICAL_JAM / COOLING_INTERRUPT / ...")
    private String faultType;

    @Schema(description = "故障描述")
    private String description;

    @Schema(description = "状态：PENDING / IN_PROGRESS / RESOLVED")
    private String status;

    @Schema(description = "优先级：HIGH / CRITICAL")
    private String priority;

    @Schema(description = "现有 work_order.assignee 字段（仅展示，与 assigneeName 不同源）")
    private String assignee;

    @Schema(description = "工单来源：AUTO（故障自动生成） / MANUAL（操作员手动创建）")
    private String source;

    @Schema(description = "故障源数据时间")
    private OffsetDateTime sourceTime;

    @Schema(description = "接受处理时间")
    private LocalDateTime acceptedAt;

    @Schema(description = "解决时间")
    private LocalDateTime resolvedAt;

    @Schema(description = "触发时温度（℃）")
    private BigDecimal latestTemperature;

    @Schema(description = "触发时振动（mm/s）")
    private BigDecimal latestVibration;

    @Schema(description = "触发时压力（kPa）")
    private BigDecimal latestPressure;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    // ---- JOIN 字段 ----
    @Schema(description = "设备编码", example = "EAF-01")
    private String deviceCode;

    @Schema(description = "设备名称", example = "电弧炉 #1")
    private String deviceName;

    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "设备区域")
    private String deviceLocation;

    @Schema(description = "指派人姓名（来自 workorder_assignment JOIN 活跃记录，最早那个）")
    private String assigneeName;

    @Schema(description = "指派人 ID（来自 workorder_assignment）")
    private Long assigneeId;

    @Schema(description = "指派时间")
    private LocalDateTime assignedAt;

    // ---- 多人指派字段（🆕 Epic 05 二次迭代） ----

    @Schema(description = "活跃指派人完整列表（JSON 字符串，来自 SQL 的 json_agg 聚合）", hidden = true)
    private String activeAssignmentsJson;

    @Schema(description = "活跃指派人完整列表（按指派时间升序，service 层解析 JSON 后填入）")
    private List<ActiveAssignmentVO> activeAssignments = new ArrayList<>();

    @Schema(description = "活跃指派人数")
    private Integer assigneeCount = 0;
}

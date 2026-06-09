package com.smartenergy.workorder.controller;

import com.smartenergy.workorder.service.DispatchDashboardService;
import com.smartenergy.workorder.vo.DispatchBoardVO;
import com.smartenergy.workorder.vo.DispatchSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workorder")
@RequiredArgsConstructor
@Tag(name = "调度总览", description = "Dispatch Summary / Board 聚合端点")
public class DispatchDashboardController {

    private final DispatchDashboardService dashboardService;

    @GetMapping("/dashboard/summary")
    @Operation(summary = "调度总览：在岗数 / 平均负载 / 技能覆盖 / 工单状态分布")
    public DispatchSummaryVO summary() {
        return dashboardService.summary();
    }

    @GetMapping("/dispatch-board")
    @Operation(summary = "调度看板：按技能分组的负载矩阵")
    public DispatchBoardVO board() {
        return dashboardService.board();
    }
}

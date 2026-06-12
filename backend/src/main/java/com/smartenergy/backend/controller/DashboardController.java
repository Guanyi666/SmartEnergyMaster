package com.smartenergy.backend.controller;

import com.smartenergy.backend.agent.ForecastService;
import com.smartenergy.backend.service.DashboardService;
import com.smartenergy.backend.vo.DashboardSummaryVO;
import com.smartenergy.backend.vo.DispatchAdviceVO;
import com.smartenergy.backend.vo.ForecastPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "大屏聚合统计与调度建议")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ForecastService forecastService;

    @GetMapping("/summary")
    @Operation(summary = "大屏聚合总览", description = "返回全厂总功耗、累计碳排放、当前电价区间、运行/离线设备数、活跃告警数、焦点设备详情")
    public DashboardSummaryVO getSummary(
            @Parameter(description = "焦点设备编码，不传则默认选择第一台设备")
            @RequestParam(required = false) String deviceCode) {
        return dashboardService.getSummary(deviceCode);
    }

    @GetMapping("/dispatch-advice")
    @Operation(summary = "调度建议", description = "基于焦点设备的实时数据与电价时段，给出 4 级调度建议（CRITICAL / WARN / GOOD / INFO）")
    public DispatchAdviceVO getDispatchAdvice(
            @Parameter(description = "设备编码") @RequestParam String deviceCode) {
        return dashboardService.getDispatchAdvice(deviceCode);
    }

    /** 焦点设备未来 +15/+30min 能耗预测（含 95% 置信区间），Epic 6-3。 */
    @GetMapping("/forecast")
    public List<ForecastPointVO> getForecast(@RequestParam String deviceCode) {
        return forecastService.getForecast(deviceCode);
    }

    /** 操作员对调度建议的确认/拒绝（Epic 6-5）。decision = CONFIRM / REJECT。 */
    @PostMapping("/dispatch-advice/decision")
    public Map<String, Object> recordDecision(@RequestBody Map<String, String> body) {
        String deviceCode = body.getOrDefault("deviceCode", "");
        String decision = body.getOrDefault("decision", "").toUpperCase();
        log.info("调度建议决策: device={} decision={}", deviceCode, decision);
        boolean confirmed = "CONFIRM".equals(decision);
        return Map.of(
                "deviceCode", deviceCode,
                "decision", decision,
                "acknowledgedAt", LocalDateTime.now().toString(),
                "message", confirmed ? "已采纳调度建议，请按建议动作执行" : "已忽略本次调度建议"
        );
    }
}

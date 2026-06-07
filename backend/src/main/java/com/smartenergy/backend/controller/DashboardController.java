package com.smartenergy.backend.controller;

import com.smartenergy.backend.service.DashboardService;
import com.smartenergy.backend.vo.DashboardSummaryVO;
import com.smartenergy.backend.vo.DispatchAdviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummaryVO getSummary(@RequestParam(required = false) String deviceCode) {
        return dashboardService.getSummary(deviceCode);
    }

    @GetMapping("/dispatch-advice")
    public DispatchAdviceVO getDispatchAdvice(@RequestParam String deviceCode) {
        return dashboardService.getDispatchAdvice(deviceCode);
    }
}

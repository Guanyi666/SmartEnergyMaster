package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.vo.WorkOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @GetMapping
    public List<WorkOrderVO> listWorkOrders(@RequestParam(required = false) String status) {
        return workOrderService.listWorkOrders(status);
    }

    @GetMapping("/active-alerts")
    public List<WorkOrderVO> listActiveAlerts(@RequestParam(defaultValue = "5") int limit) {
        return workOrderService.listActiveAlerts(limit);
    }

    @PatchMapping("/{id}/status")
    public WorkOrderVO updateStatus(@PathVariable Long id, @Valid @RequestBody WorkOrderStatusRequest request) {
        return workOrderService.updateStatus(id, request);
    }
}

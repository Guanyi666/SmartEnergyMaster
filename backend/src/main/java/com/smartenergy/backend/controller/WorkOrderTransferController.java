package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.TransferRequestCreateRequest;
import com.smartenergy.backend.dto.TransferRequestReviewRequest;
import com.smartenergy.backend.service.WorkOrderTransferService;
import com.smartenergy.backend.vo.WorkOrderTransferRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderTransferController {

    private final WorkOrderTransferService transferService;

    @PostMapping("/{id}/transfer-requests")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_ENGINEER', 'ADMIN')")
    public WorkOrderTransferRequestVO create(
            @PathVariable Long id,
            @Valid @RequestBody TransferRequestCreateRequest request) {
        return transferService.create(id, request);
    }

    @GetMapping("/transfer-requests")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_ENGINEER', 'DEVICE_MANAGER', 'ADMIN')")
    public List<WorkOrderTransferRequestVO> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine) {
        return transferService.list(status, mine);
    }

    @PatchMapping("/transfer-requests/{id}/review")
    @PreAuthorize("hasAnyAuthority('DEVICE_MANAGER', 'ADMIN')")
    public WorkOrderTransferRequestVO review(
            @PathVariable Long id,
            @Valid @RequestBody TransferRequestReviewRequest request) {
        return transferService.review(id, request);
    }
}

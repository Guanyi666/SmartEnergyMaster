package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.TransferRequestCreateRequest;
import com.smartenergy.backend.dto.TransferRequestReviewRequest;
import com.smartenergy.backend.vo.WorkOrderTransferRequestVO;

import java.util.List;

public interface WorkOrderTransferService {

    WorkOrderTransferRequestVO create(Long workOrderId, TransferRequestCreateRequest request);

    List<WorkOrderTransferRequestVO> list(String status, boolean mine);

    WorkOrderTransferRequestVO review(Long id, TransferRequestReviewRequest request);
}

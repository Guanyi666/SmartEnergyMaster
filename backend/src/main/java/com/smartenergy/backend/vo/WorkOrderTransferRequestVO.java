package com.smartenergy.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkOrderTransferRequestVO {

    private Long id;
    private Long workOrderId;
    private String workOrderNo;
    private String workOrderTitle;
    private String deviceName;
    private Long requesterPersonnelId;
    private String requesterEmployeeNo;
    private String requesterName;
    private String reason;
    private String status;
    private String reviewerUsername;
    private Long newPersonnelId;
    private String newPersonnelName;
    private String reviewNote;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
}

package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_transfer_request")
public class WorkOrderTransferRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long requesterPersonnelId;
    private String reason;
    private String status;
    private String reviewerUsername;
    private Long newPersonnelId;
    private String reviewNote;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
}

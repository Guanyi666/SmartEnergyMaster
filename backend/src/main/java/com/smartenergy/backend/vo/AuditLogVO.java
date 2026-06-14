package com.smartenergy.backend.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AuditLogVO {
    private Long id;
    private String actorUsername;
    private String action;
    private String module;
    private String targetType;
    private String targetId;
    private String detail;
    private OffsetDateTime createdAt;
}

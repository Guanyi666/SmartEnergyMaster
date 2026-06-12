package com.smartenergy.backend.service;

import com.smartenergy.backend.vo.AuditLogVO;
import com.smartenergy.backend.vo.PageVO;

import java.time.OffsetDateTime;
import java.util.Map;

public interface AuditLogService {
    void record(String action, String module, String targetType, String targetId, Map<String, Object> detail);

    PageVO<AuditLogVO> list(int page, int size, String action, String module,
                            OffsetDateTime startAt, OffsetDateTime endAt);
}

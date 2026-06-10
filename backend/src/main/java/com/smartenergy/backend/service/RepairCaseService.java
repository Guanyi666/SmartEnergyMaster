package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.CaseCreateRequest;
import com.smartenergy.backend.entity.RepairCase;
import com.smartenergy.backend.vo.CaseDetailVO;

import java.util.List;

public interface RepairCaseService {

    List<CaseDetailVO> listCases(String deviceType, String faultType, String keyword);

    CaseDetailVO getDetail(Long id);

    CaseDetailVO createCase(CaseCreateRequest request);

    CaseDetailVO updateCase(Long id, CaseCreateRequest request);

    /**
     * 从已闭环工单提炼为维修案例。
     */
    CaseDetailVO createCaseFromWorkOrder(Long workOrderId, String title, String rootCause, String repairProcess, String technician);

    /**
     * 按设备类型+故障类型+关键词查找相似案例，最多返回 limit 条。
     * 评分 = 设备类型匹配(3) + 故障类型匹配(2) + 关键词命中(1)。
     */
    List<CaseDetailVO> findSimilar(String deviceType, String faultType, String keyword, int limit);

    void deleteCase(Long id);
}
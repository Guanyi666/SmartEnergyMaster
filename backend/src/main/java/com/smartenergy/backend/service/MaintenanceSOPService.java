package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.SOPCreateRequest;
import com.smartenergy.backend.dto.SOPUpdateRequest;
import com.smartenergy.backend.vo.SOPDetailVO;

import java.util.List;

public interface MaintenanceSOPService {

    List<SOPDetailVO> listSOPs(String deviceType, String faultType, String keyword);

    SOPDetailVO getDetail(Long id);

    SOPDetailVO createSOP(SOPCreateRequest request);

    SOPDetailVO updateSOP(Long id, SOPUpdateRequest request);

    void deleteSOP(Long id);

    /**
     * 最佳匹配：工单创建时按 deviceType+faultType 找出 SOP 编号，未命中返回 null。
     * 匹配优先级：精确类型匹配 > 设备类型匹配 > 同故障类型匹配。
     */
    Long matchSOPId(String deviceType, String faultType);

    /**
     * 给 AI 诊断用：返回匹配的 SOP 全文内容。
     */
    SOPDetailVO findBestMatch(String deviceType, String faultType);
}
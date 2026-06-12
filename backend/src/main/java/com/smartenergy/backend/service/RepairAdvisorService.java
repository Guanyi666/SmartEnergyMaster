package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.RepairAdviceRequest;
import com.smartenergy.backend.vo.RepairAdviceVO;

public interface RepairAdvisorService {
    /**
     * 生成 AI 维修建议：
     * 1) 调 MaintenanceSOPService.findBestMatch 拉匹配 SOP
     * 2) 尝试 LLM 增强（未配 apiKey 时降级到纯 SOP 路径）
     * 3) 每个步骤带 sourceSopId / sourceStepIndex / confidence
     */
    RepairAdviceVO generateAdvice(RepairAdviceRequest request);
}
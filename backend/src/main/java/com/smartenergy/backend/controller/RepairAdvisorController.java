package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.RepairAdviceRequest;
import com.smartenergy.backend.service.RepairAdvisorService;
import com.smartenergy.backend.vo.RepairAdviceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI 维修建议", description = "RepairAdvisorAgent 调 SOP 检索 + 步骤与 SOP 逐条对照 (Epic 07-1-2)")
public class RepairAdvisorController {

    private final RepairAdvisorService advisorService;

    @PostMapping("/repair-advice")
    @Operation(summary = "生成 AI 维修建议",
            description = "内部流程：1) 调 SOP 检索接口找最佳匹配 SOP；2) 尝试 LLM 增强（未配 API key 时降级到纯 SOP 路径）；3) 返回每个步骤的 SOP 源引用与置信度")
    public RepairAdviceVO generateAdvice(@Valid @RequestBody RepairAdviceRequest request) {
        return advisorService.generateAdvice(request);
    }
}
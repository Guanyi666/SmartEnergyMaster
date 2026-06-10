package com.smartenergy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.dto.RepairAdviceRequest;
import com.smartenergy.backend.dto.RepairAdviceStep;
import com.smartenergy.backend.llm.LlmClient;
import com.smartenergy.backend.service.MaintenanceSOPService;
import com.smartenergy.backend.service.RepairAdvisorService;
import com.smartenergy.backend.vo.RepairAdviceVO;
import com.smartenergy.backend.vo.SOPDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairAdvisorServiceImpl implements RepairAdvisorService {

    private final MaintenanceSOPService sopService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    public RepairAdviceVO generateAdvice(RepairAdviceRequest request) {
        // 1) 拉匹配 SOP
        SOPDetailVO sop = sopService.findBestMatch(request.getDeviceType(), request.getFaultType());
        if (sop == null) {
            log.info("[Advisor] no SOP matched for deviceType={}, faultType={}", request.getDeviceType(), request.getFaultType());
            return emptyAdvice(request, "未在知识库中找到匹配的 SOP，请补充设备/故障信息或新建 SOP。");
        }

        // 2) 步骤数组（来自 SOP 的 steps JSON）
        List<String> sopSteps = parseJsonArray(sop.getSteps());
        if (sopSteps.isEmpty()) {
            return emptyAdvice(request, "匹配到的 SOP 暂无具体步骤，请人工补全。");
        }

        // 3) 决定走 LLM 还是降级
        boolean useLlm = Boolean.TRUE.equals(request.getUseLlm()) && llmClient.isLive();
        if (useLlm) {
            try {
                return generateWithLlm(request, sop, sopSteps);
            } catch (Exception ex) {
                log.warn("[Advisor] LLM 路径失败，降级到纯 SOP 路径: {}", ex.getMessage());
            }
        }
        return generateDeterministic(request, sop, sopSteps, "未配置 LLM 或调用失败，已按 SOP 步骤生成");
    }

    private RepairAdviceVO generateDeterministic(RepairAdviceRequest request, SOPDetailVO sop, List<String> sopSteps, String reason) {
        RepairAdviceVO vo = new RepairAdviceVO();
        vo.setWorkOrderId(request.getWorkOrderId());
        vo.setMatchedSop(sop);
        vo.setStrategy("DETERMINISTIC");
        vo.setGeneratedAt(LocalDateTime.now());

        List<RepairAdviceStep> steps = new ArrayList<>();
        for (int i = 0; i < sopSteps.size(); i++) {
            RepairAdviceStep s = new RepairAdviceStep();
            s.setOrder(i + 1);
            s.setAction(sopSteps.get(i));
            s.setSourceSopId(sop.getId());
            s.setSourceSopCode(sop.getSopCode());
            s.setSourceStepIndex(i);
            s.setConfidence(1.0);
            s.setAiDerived(false);
            steps.add(s);
        }
        vo.setSteps(steps);
        vo.setOverallConfidence(1.0);
        vo.setSummary("参考 SOP [" + sop.getSopCode() + "] " + sop.getTitle() + " 共 " + sopSteps.size() + " 步。");
        return vo;
    }

    private RepairAdviceVO generateWithLlm(RepairAdviceRequest request, SOPDetailVO sop, List<String> sopSteps) {
        // 构造 prompt：要求 LLM 返回 JSON 数组，每个元素含 order/action/sourceStepIndex/confidence/rationale
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是钢铁设备维修专家。现在有一个故障，请参考下面的 SOP 输出一步步的维修建议。\n\n");
        prompt.append("【故障信息】\n");
        prompt.append("- 设备类型：").append(request.getDeviceType()).append("\n");
        prompt.append("- 故障类型：").append(request.getFaultType()).append("\n");
        if (request.getSymptoms() != null && !request.getSymptoms().isBlank()) {
            prompt.append("- 现场描述：").append(request.getSymptoms()).append("\n");
        }
        prompt.append("\n【参考 SOP】").append(sop.getSopCode()).append(" - ").append(sop.getTitle()).append("\n");
        prompt.append("安全提示：").append(sop.getSummary()).append("\n");
        for (int i = 0; i < sopSteps.size(); i++) {
            prompt.append("步骤 ").append(i + 1).append("：").append(sopSteps.get(i)).append("\n");
        }
        prompt.append("\n【输出要求】仅返回严格 JSON 数组（不要 markdown 代码块），每个元素字段：\n");
        prompt.append("{\"order\":1,\"action\":\"...\",\"sourceStepIndex\":0,\"confidence\":0.9,\"rationale\":\"...\"}\n");
        prompt.append("- order 从 1 起递增；\n");
        prompt.append("- action 是这一步的动作文本；\n");
        prompt.append("- sourceStepIndex 是参考的 SOP 步骤下标（0=").append(sopSteps.size() - 1).append("），如果该步是 AI 推断请填 -1；\n");
        prompt.append("- confidence 0~1；\n");
        prompt.append("- rationale 是简短理由，可空。\n");

        String raw = llmClient.chat(prompt.toString());
        List<JsonNode> items = parseLlmJsonArray(raw);

        RepairAdviceVO vo = new RepairAdviceVO();
        vo.setWorkOrderId(request.getWorkOrderId());
        vo.setMatchedSop(sop);
        vo.setStrategy("LLM");
        vo.setGeneratedAt(LocalDateTime.now());

        List<RepairAdviceStep> steps = new ArrayList<>();
        double totalConf = 0;
        for (JsonNode n : items) {
            RepairAdviceStep s = new RepairAdviceStep();
            int order = n.path("order").asInt(steps.size() + 1);
            s.setOrder(order);
            s.setAction(n.path("action").asText(""));
            int srcIdx = n.path("sourceStepIndex").asInt(-1);
            if (srcIdx >= 0 && srcIdx < sopSteps.size()) {
                s.setSourceSopId(sop.getId());
                s.setSourceSopCode(sop.getSopCode());
                s.setSourceStepIndex(srcIdx);
                s.setAiDerived(false);
            } else {
                s.setSourceStepIndex(-1);
                s.setAiDerived(true);
            }
            double conf = n.path("confidence").asDouble(0.6);
            if (conf < 0) conf = 0;
            if (conf > 1) conf = 1;
            s.setConfidence(conf);
            s.setRationale(n.path("rationale").asText(null));
            steps.add(s);
            totalConf += conf;
        }
        // LLM 没返回任何 step 时降级
        if (steps.isEmpty()) {
            log.warn("[Advisor] LLM 返回空步骤，降级到 deterministic");
            return generateDeterministic(request, sop, sopSteps, "LLM 返回为空");
        }
        vo.setSteps(steps);
        vo.setOverallConfidence(Math.min(1.0, totalConf / steps.size()));
        vo.setSummary("基于 SOP [" + sop.getSopCode() + "] 生成 " + steps.size() + " 步建议；其中 "
                + steps.stream().filter(s -> Boolean.TRUE.equals(s.getAiDerived())).count() + " 步为 AI 推断。");
        return vo;
    }

    private RepairAdviceVO emptyAdvice(RepairAdviceRequest request, String summary) {
        RepairAdviceVO vo = new RepairAdviceVO();
        vo.setWorkOrderId(request.getWorkOrderId());
        vo.setStrategy("DETERMINISTIC");
        vo.setSummary(summary);
        vo.setOverallConfidence(0.0);
        vo.setSteps(new ArrayList<>());
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    private List<String> parseJsonArray(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        node.forEach(child -> out.add(child.asText("")));
        return out;
    }

    private List<JsonNode> parseLlmJsonArray(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            // 容忍 LLM 返回 ```json ... ``` 包裹
            String trimmed = raw.trim();
            if (trimmed.startsWith("```")) {
                int first = trimmed.indexOf('\n');
                int last = trimmed.lastIndexOf("```");
                if (first > 0 && last > first) {
                    trimmed = trimmed.substring(first + 1, last).trim();
                }
            }
            JsonNode root = objectMapper.readTree(trimmed);
            JsonNode arr = root.isArray() ? root : root.path("steps");
            if (!arr.isArray()) return List.of();
            List<JsonNode> list = new ArrayList<>();
            arr.forEach(list::add);
            return list;
        } catch (Exception ex) {
            log.warn("[Advisor] LLM 返回解析失败: {}", ex.getMessage());
            return List.of();
        }
    }
}
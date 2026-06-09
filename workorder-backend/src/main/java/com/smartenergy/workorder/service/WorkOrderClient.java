package com.smartenergy.workorder.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调现有 8080 后端 WorkOrderController 的 PATCH /api/work-orders/{id}/status
 *
 * 🟠 脏写风险修正：assign 接口内部通过本 client 写 work_order.assignee
 *    新模块本身不直接 UPDATE work_order 表任何列
 *
 * 🆕 Epic 05 二次迭代：抽出 syncAssigneeTo8080(workOrderId, fullNameList)
 *    供 assign / release / replace / batchAssign 统一复用，保证老字段同步逻辑一致
 *
 * 🆕 关键修复：专用 ObjectMapper 强制 INCLUDE null 字段
 *   application.yml 配置了 spring.jackson.default-property-inclusion: non_null，
 *   默认 RestTemplate 用的就是这个 ObjectMapper，会把 null 字段从 JSON 里剔除。
 *   8080 端的 controller 用 request.containsKey("assignee") 区分"未传"和"传 null"，
 *   一旦 8081 发的 body 把 assignee 字段剔除了，8080 就把它当成"未传"→ 字段保持原值，
 *   "拖回 PENDING 时 8080 字段残留"就是这个原因。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderClient {

    /**
     * 专用 ObjectMapper：强制保留 null 字段（与 application.yml 的 non_null 相反）
     */
    private static final ObjectMapper INCLUDE_NULL_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private final RestTemplate restTemplate;

    @Value("${existing-backend.base-url}")
    private String baseUrl;

    /**
     * 同步 work_order.assignee 老字段到 8080
     *
     * @param workOrderId 工单 ID
     * @param currentStatus 工单当前 status（保持不变，PATCH 时只改 assignee）
     * @param fullNameList 完整活跃指派人姓名列表（已去重 + 排序）
     *                     为 null 时 8080 把 assignee 字段清空
     * @param note 备注（null=不变）
     */
    public boolean syncAssigneeTo8080(Long workOrderId, String currentStatus,
                                       List<String> fullNameList, String note) {
        String url = baseUrl + "/api/work-orders/" + workOrderId + "/status";
        Map<String, Object> body = new HashMap<>();
        body.put("status", currentStatus);
        // assignee 字段：null 显式置空，否则发字符串
        if (fullNameList == null) {
            body.put("assignee", null);
        } else {
            // 格式：完整名逗号分隔（如"张工,王工,赵工"）
            // 超 64 字符截断 + "+N" 兜底（每个 Chinese 算 1 字符，PG VARCHAR(64) = 64 chars）
            body.put("assignee", formatAssigneeString(fullNameList));
        }
        if (note != null) body.put("note", note);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 🆕 关键修复：手动序列化为 JSON 字符串，专用 INCLUDE_NULL_MAPPER 保证
        //    即便 body 中有 null 值，序列化结果里也会保留 "assignee": null。
        //    之前用 RestTemplate 自动序列化时被全局 non_null 策略吃掉，8080 收不到 assignee 字段。
        String jsonBody;
        try {
            jsonBody = INCLUDE_NULL_MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("syncAssignee JSON 序列化失败", e);
        }

        try {
            log.debug("[WorkOrderClient] syncAssignee id={} body={}", workOrderId, jsonBody);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.PATCH, new HttpEntity<>(jsonBody, headers), String.class);
            int code = response.getStatusCode().value();
            log.info("[WorkOrderClient] syncAssignee id={} → {} ({}名)", workOrderId, code,
                    fullNameList == null ? 0 : fullNameList.size());
            return code == 200;
        } catch (RestClientException ex) {
            log.error("[WorkOrderClient] syncAssignee 失败: id={}, body={}, error={}", workOrderId, jsonBody, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 兜底：把指派人姓名列表格式化为 <= 64 字符的字符串
     * 优先完整逗号分隔；溢出时保留第 1 个 + "+N"
     */
    private String formatAssigneeString(List<String> names) {
        if (names == null || names.isEmpty()) return null;
        String joined = String.join(",", names);
        if (joined.length() <= 64) return joined;
        // 溢出：保留第 1 个名字 + "+N" 形式
        int overflow = names.size() - 1;
        String suffix = " +" + overflow;
        int budget = 64 - suffix.length();
        String first = names.get(0);
        if (first.length() > budget) {
            return first.substring(0, Math.max(0, budget)) + suffix;
        }
        return first + suffix;
    }
}

package com.smartenergy.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 🆕 合并 workorder-backend: 替代原来的 WorkOrderClient（跨进程 HTTP 调用）
 *
 * 之前 8081 的 WorkOrderClient 用 RestTemplate 调 8080 的 PATCH /api/work-orders/{id}/status，
 * 引入了 5+3 个连环 bug（Jackson non_null 吃 null、FieldStrategy.NOT_NULL、写不一致等）。
 * 合并后变成同模块方法调用：
 *   - 不再走 HTTP，没有序列化/反序列化
 *   - 同一事务（@Transactional 边界可延伸到上游的 assign/release/replace）
 *   - 不再有 8080 PATCH 端点的 Map/assigneeProvided 边界条件
 *
 * 替代关系：workOrderClient.syncAssigneeTo8080(workOrderId, status, fullNameList, null)
 *           → workOrderSyncService.sync(workOrderId, status, fullNameList)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderSyncService {

    private final WorkOrderService workOrderService;

    /**
     * 同步 work_order.assignee 老字段
     *
     * 🟢 之前走 workOrderService.updateStatus(...) 完整流程，会触发 syncDeviceStatusAfterOrderUpdate 双重写；
     *   现在改用专用 updateAssignee() 方法，干净地只改 assignee 字段。
     *
     * @param workOrderId  工单 ID
     * @param fullNameList 完整活跃指派人姓名列表
     *                     - null  → 清空 assignee
     *                     - 非空  → 写入（>64 字符预截断 + "+N"）
     */
    public void sync(Long workOrderId, List<String> fullNameList) {
        String formatted = formatAssigneeString(fullNameList);
        workOrderService.updateAssignee(workOrderId, formatted);
        log.debug("[WorkOrderSync] id={} names={}", workOrderId, fullNameList == null ? 0 : fullNameList.size());
    }

    /**
     * 把指派人姓名列表格式化为 <= 64 字符的字符串
     * 优先完整逗号分隔；溢出时保留第 1 个 + "+N"
     * （从原 WorkOrderClient.formatAssigneeString 搬过来，行为一致）
     */
    private String formatAssigneeString(List<String> names) {
        if (names == null || names.isEmpty()) return null;
        String joined = String.join(",", names);
        if (joined.length() <= 64) return joined;
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

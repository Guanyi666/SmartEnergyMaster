package com.smartenergy.workorder.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.workorder.dto.BatchWorkOrderAssignRequest;
import com.smartenergy.workorder.dto.WorkOrderAssignRequest;
import com.smartenergy.workorder.dto.WorkOrderReplaceRequest;
import com.smartenergy.workorder.vo.DispatchMatchVO;

public interface WorkOrderAssignmentService {

    /**
     * 单人指派
     * 事务内：插入 workorder_assignment + personnel.current_workload+1
     *        + 调 8080 同步 work_order.assignee
     */
    void assign(Long workOrderId, WorkOrderAssignRequest req);

    /**
     * 🆕 批量指派：把多个人同时指到同一工单
     * 事务内：N 个人的 4 步校验（存在/在岗/未重复/有容量）→ 全部通过才插入
     *        → 各自 workload+1 → 调 8080 同步 1 次 assignee
     * 任一失败全 rollback
     */
    void batchAssign(Long workOrderId, BatchWorkOrderAssignRequest req);

    /**
     * 🆕 单条释放：把某个具体人员从工单上撤下来
     * 事务内：标记该 personnel 的活跃行 released_at=now + workload-1
     *        + 调 8080 同步 assignee 字符串
     */
    void release(Long workOrderId, Long personnelId);

    /**
     * 🆕 替换：把某个旧人员从工单上撤下，同时指派新人员
     * 事务内：标记旧行 released_at + 旧人员 workload-1 + 插入新行 + 新人员 workload+1
     *        + 调 8080 同步 1 次 assignee
     */
    void replace(Long workOrderId, Long oldPersonnelId, WorkOrderReplaceRequest req);

    /**
     * 释放该工单所有活跃指派（保留端点，向后兼容）
     */
    void release(Long workOrderId);

    /**
     * 自动匹配 Top N
     */
    DispatchMatchVO autoMatch(Long workOrderId, String faultType, int topN);
}

package com.smartenergy.workorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.workorder.dto.BatchWorkOrderAssignRequest;
import com.smartenergy.workorder.dto.WorkOrderAssignRequest;
import com.smartenergy.workorder.dto.WorkOrderReplaceRequest;
import com.smartenergy.workorder.entity.ExistingWorkOrder;
import com.smartenergy.workorder.entity.MaintenancePersonnel;
import com.smartenergy.workorder.entity.WorkOrderAssignment;
import com.smartenergy.workorder.mapper.ExistingWorkOrderMapper;
import com.smartenergy.workorder.mapper.MaintenancePersonnelMapper;
import com.smartenergy.workorder.mapper.WorkOrderAssignmentMapper;
import com.smartenergy.workorder.service.AutoMatchEngine;
import com.smartenergy.workorder.service.WorkOrderAssignmentService;
import com.smartenergy.workorder.service.WorkOrderClient;
import com.smartenergy.workorder.vo.DispatchMatchVO;
import com.smartenergy.workorder.vo.MatchCandidateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderAssignmentServiceImpl implements WorkOrderAssignmentService {

    private final WorkOrderAssignmentMapper assignmentMapper;
    private final MaintenancePersonnelMapper personnelMapper;
    private final ExistingWorkOrderMapper existingWorkOrderMapper;
    private final WorkOrderClient workOrderClient;
    private final AutoMatchEngine autoMatchEngine;

    // ============================================================
    // 🆕 二次迭代：单人指派 / 批量指派 / 单条释放 / 替换 / 释放全部
    // 所有方法统一用 syncAssigneeTo8080 同步老字段
    // ============================================================

    @Override
    @Transactional
    public void assign(Long workOrderId, WorkOrderAssignRequest req) {
        // 1. 校验工单 + 人员（不变量）
        ExistingWorkOrder workOrder = requireWorkOrder(workOrderId);
        MaintenancePersonnel personnel = requireAvailablePersonnel(req.getPersonnelId(), workOrderId);

        // 2. 写新行
        insertAssignment(workOrderId, req.getPersonnelId(),
                StringUtils.hasText(req.getRole()) ? req.getRole().toUpperCase() : "PRIMARY",
                req.getNote());

        // 3. workload +1
        bumpWorkload(req.getPersonnelId(), +1);

        // 4. 同步老字段
        syncAssigneeTo8080(workOrderId, workOrder.getStatus());
    }

    @Override
    @Transactional
    public void batchAssign(Long workOrderId, BatchWorkOrderAssignRequest req) {
        // 1. 校验工单
        ExistingWorkOrder workOrder = requireWorkOrder(workOrderId);
        if ("RESOLVED".equals(workOrder.getStatus())) {
            throw new IllegalStateException("工单已闭环，无法指派");
        }

        List<Long> ids = req.getPersonnelIds();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("人员 ID 列表不能为空");
        }
        // 去重（防止客户端传 [1,1,2]）
        List<Long> distinctIds = ids.stream().distinct().toList();
        String role = StringUtils.hasText(req.getRole()) ? req.getRole().toUpperCase() : "PRIMARY";

        // 2. 预校验 N 个：存在 + 在岗 + 未重复指派 + 有容量
        //    任一不通过立刻抛异常，事务回滚（前面无 INSERT 不会脏写）
        List<MaintenancePersonnel> validated = new ArrayList<>();
        for (Long pid : distinctIds) {
            MaintenancePersonnel p = requireAvailablePersonnel(pid, workOrderId);
            validated.add(p);
        }

        // 3. 全部 OK，循环插入 + workload+1
        for (Long pid : distinctIds) {
            insertAssignment(workOrderId, pid, role, req.getNote());
            bumpWorkload(pid, +1);
        }

        // 4. 调 8080 同步老字段（只 1 次 PATCH）
        syncAssigneeTo8080(workOrderId, workOrder.getStatus());

        log.info("[BatchAssign] workOrderId={} 批量指派 {} 人: {}", workOrderId, distinctIds.size(), distinctIds);
    }

    @Override
    @Transactional
    public void release(Long workOrderId, Long personnelId) {
        // 1. 校验
        ExistingWorkOrder workOrder = requireWorkOrder(workOrderId);
        // 🟠 修：personnel 可能已被删（孤儿指派），此时静默释放，不抛异常
        MaintenancePersonnel personnel = personnelMapper.selectById(personnelId);

        // 2. 找该 personnel 的活跃指派
        WorkOrderAssignment active = assignmentMapper.selectOne(
                new QueryWrapper<WorkOrderAssignment>()
                        .eq("work_order_id", workOrderId)
                        .eq("personnel_id", personnelId)
                        .isNull("released_at"));
        if (active == null) {
            throw new IllegalStateException("该 personnel 在此工单上没有活跃指派");
        }

        // 3. 标记 released_at
        active.setReleasedAt(LocalDateTime.now());
        assignmentMapper.updateById(active);

        // 4. workload -1（personnel 不存在时跳过）
        if (personnel != null) {
            bumpWorkload(personnelId, -1);
        } else {
            log.warn("[ReleaseOne] personnelId={} 已不存在，跳过 workload-1", personnelId);
        }

        // 5. 同步老字段
        syncAssigneeTo8080(workOrderId, workOrder.getStatus());
        log.info("[ReleaseOne] workOrderId={} 释放 personnelId={} ({})",
                workOrderId, personnelId, personnel == null ? "<已删除>" : personnel.getName());
    }

    @Override
    @Transactional
    public void replace(Long workOrderId, Long oldPersonnelId, WorkOrderReplaceRequest req) {
        if (oldPersonnelId == null) throw new IllegalArgumentException("oldPersonnelId 不能为空");
        if (req == null || req.getNewPersonnelId() == null) throw new IllegalArgumentException("newPersonnelId 不能为空");
        if (oldPersonnelId.equals(req.getNewPersonnelId())) {
            throw new IllegalArgumentException("新旧人员不能是同一个人");
        }

        // 1. 校验工单
        ExistingWorkOrder workOrder = requireWorkOrder(workOrderId);
        if ("RESOLVED".equals(workOrder.getStatus())) {
            throw new IllegalStateException("工单已闭环，无法换人");
        }

        // 2. 找旧人员的活跃指派（personnel 已删也可静默替换）
        MaintenancePersonnel oldP = personnelMapper.selectById(oldPersonnelId);
        WorkOrderAssignment oldActive = assignmentMapper.selectOne(
                new QueryWrapper<WorkOrderAssignment>()
                        .eq("work_order_id", workOrderId)
                        .eq("personnel_id", oldPersonnelId)
                        .isNull("released_at"));
        if (oldActive == null) {
            throw new IllegalStateException("旧人员不在该工单的活跃指派列表中");
        }

        // 3. 校验新人员可用
        MaintenancePersonnel newP = requireAvailablePersonnel(req.getNewPersonnelId(), workOrderId);

        // 4. 事务：释放旧 + 插入新
        oldActive.setReleasedAt(LocalDateTime.now());
        assignmentMapper.updateById(oldActive);
        // oldPersonnel 不存在时跳过 workload-1
        if (oldP != null) {
            bumpWorkload(oldPersonnelId, -1);
        } else {
            log.warn("[Replace] 旧 personnelId={} 已不存在，跳过 workload-1", oldPersonnelId);
        }

        insertAssignment(workOrderId, req.getNewPersonnelId(),
                oldActive.getRole(), // 沿用旧的角色
                req.getNote() != null ? req.getNote() : oldActive.getNote());
        bumpWorkload(req.getNewPersonnelId(), +1);

        // 5. 同步老字段
        syncAssigneeTo8080(workOrderId, workOrder.getStatus());

        log.info("[Replace] workOrderId={} {} → {}",
                workOrderId,
                oldP == null ? "<已删除>" : oldP.getName(),
                newP.getName());
    }

    @Override
    @Transactional
    public void release(Long workOrderId) {
        // 🟢 修复：兼容旧端点 + 幂等性
        // 1. 释放该工单所有活跃指派（如果有）
        // 2. 即使没有活跃指派，也要把 8080 的 assignee 老字段清空
        //    解决"拖回 PENDING 时显示仍有指派人"的问题
        ExistingWorkOrder workOrder = requireWorkOrder(workOrderId);
        List<WorkOrderAssignment> actives = assignmentMapper.selectList(
                new QueryWrapper<WorkOrderAssignment>()
                        .eq("work_order_id", workOrderId)
                        .isNull("released_at"));

        LocalDateTime now = LocalDateTime.now();
        for (WorkOrderAssignment a : actives) {
            a.setReleasedAt(now);
            assignmentMapper.updateById(a);
            bumpWorkload(a.getPersonnelId(), -1);
        }
        // 同步老字段：传 null 表示清空 assignee（即使 actives 为空也必须同步）
        try {
            workOrderClient.syncAssigneeTo8080(workOrderId, workOrder.getStatus(), null, null);
        } catch (Exception ex) {
            log.error("[ReleaseAll] 8080 同步失败，事务回滚: workOrderId={}", workOrderId, ex);
            throw new IllegalStateException("现有 8080 后端同步失败，释放已回滚: " + ex.getMessage());
        }
        if (actives.isEmpty()) {
            log.info("[ReleaseAll] workOrderId={} 无活跃指派，仅同步 8080 清空 assignee", workOrderId);
        } else {
            log.info("[ReleaseAll] workOrderId={} 释放 {} 个指派", workOrderId, actives.size());
        }
    }

    // ============================================================
    // 保留：自动匹配
    // ============================================================

    @Override
    public DispatchMatchVO autoMatch(Long workOrderId, String faultType, int topN) {
        List<String> required = autoMatchEngine.resolveRequiredSkills(faultType);
        if (required.isEmpty()) {
            log.warn("[AutoMatch] 未知 faultType={}，返回空", faultType);
        }

        List<MaintenancePersonnel> candidates = personnelMapper.selectList(
                new QueryWrapper<MaintenancePersonnel>().eq("is_on_duty", true));

        // 🔴 修正：之前用 `isNotNull("released_at")` 排除"曾被 release 过"的人员，
        //   导致一个工单历史上把所有师傅都指派过一遍后，再次调度就没有任何候选。
        //   正确逻辑：只排除**当前活跃指派**（同一工单同时被指派给多人时应去重），
        //   而 released 记录不影响——释放后允许重新派遣。
        if (workOrderId != null) {
            List<Long> activeIds = assignmentMapper.selectList(
                    new QueryWrapper<WorkOrderAssignment>()
                            .eq("work_order_id", workOrderId)
                            .isNull("released_at"))
                    .stream()
                    .map(WorkOrderAssignment::getPersonnelId)
                    .distinct()
                    .toList();
            if (!activeIds.isEmpty()) {
                candidates = candidates.stream()
                        .filter(p -> !activeIds.contains(p.getId()))
                        .toList();
            }
        }

        List<MatchCandidateVO> ranked = autoMatchEngine.rankCandidates(candidates, required, topN);

        DispatchMatchVO vo = new DispatchMatchVO();
        vo.setWorkOrderId(workOrderId);
        vo.setFaultType(faultType);
        vo.setRequiredSkills(required);
        vo.setCandidates(ranked);
        return vo;
    }

    // ============================================================
    // 内部辅助方法
    // ============================================================

    /** 校验工单存在 + 未闭环 */
    private ExistingWorkOrder requireWorkOrder(Long workOrderId) {
        ExistingWorkOrder workOrder = existingWorkOrderMapper.selectById(workOrderId);
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在: id=" + workOrderId);
        }
        return workOrder;
    }

    /**
     * 校验人员存在 + 在岗 + 未在该工单重复指派 + 有容量
     * 返回完整 entity 供后续使用
     */
    private MaintenancePersonnel requireAvailablePersonnel(Long personnelId, Long workOrderId) {
        MaintenancePersonnel p = personnelMapper.selectById(personnelId);
        if (p == null) {
            throw new IllegalArgumentException("人员不存在: id=" + personnelId);
        }
        if (Boolean.FALSE.equals(p.getIsOnDuty())) {
            throw new IllegalStateException(p.getName() + " 当前离岗，无法指派");
        }
        if (workOrderId != null) {
            // 重复指派检查
            Long dup = assignmentMapper.selectCount(new QueryWrapper<WorkOrderAssignment>()
                    .eq("work_order_id", workOrderId)
                    .eq("personnel_id", personnelId)
                    .isNull("released_at"));
            if (dup != null && dup > 0) {
                throw new IllegalStateException(p.getName() + " 已经在该工单上有活跃指派");
            }
        }
        int current = p.getCurrentWorkload() == null ? 0 : p.getCurrentWorkload();
        int max = p.getMaxWorkload() == null ? 0 : p.getMaxWorkload();
        if (current >= max) {
            throw new IllegalStateException(
                p.getName() + " 已达最大工作负载 " + max + "，无法再指派"
            );
        }
        return p;
    }

    /** 插入 workorder_assignment 行 */
    private void insertAssignment(Long workOrderId, Long personnelId, String role, String note) {
        WorkOrderAssignment a = new WorkOrderAssignment();
        a.setWorkOrderId(workOrderId);
        a.setPersonnelId(personnelId);
        a.setRole(role);
        a.setAssignedAt(LocalDateTime.now());
        a.setNote(note);
        assignmentMapper.insert(a);
    }

    /** personnel.current_workload += delta，delta 必须为 ±1 */
    private void bumpWorkload(Long personnelId, int delta) {
        MaintenancePersonnel p = personnelMapper.selectById(personnelId);
        if (p == null) return; // 理论不会发生（前面已校验过）
        int cw = p.getCurrentWorkload() == null ? 0 : p.getCurrentWorkload();
        p.setCurrentWorkload(Math.max(0, cw + delta));
        p.setUpdatedAt(LocalDateTime.now());
        personnelMapper.updateById(p);
    }

    /**
     * 重新计算工单所有活跃指派人姓名，格式化后 PATCH 8080 同步 assignee 老字段
     * WorkOrderClient 失败时事务回滚
     */
    private void syncAssigneeTo8080(Long workOrderId, String currentStatus) {
        List<String> activeNames = new ArrayList<>();
        List<WorkOrderAssignment> actives = assignmentMapper.selectList(
                new QueryWrapper<WorkOrderAssignment>()
                        .eq("work_order_id", workOrderId)
                        .isNull("released_at")
                        .orderByAsc("assigned_at"));
        for (WorkOrderAssignment a : actives) {
            MaintenancePersonnel p = personnelMapper.selectById(a.getPersonnelId());
            if (p != null) activeNames.add(p.getName());
        }
        try {
            // 空列表传 null（WorkOrderClient 会发 assignee=null 让 8080 清空字段）
            workOrderClient.syncAssigneeTo8080(workOrderId, currentStatus,
                    activeNames.isEmpty() ? null : activeNames, null);
        } catch (Exception ex) {
            log.error("[Sync] 8080 同步失败，事务回滚: workOrderId={}", workOrderId, ex);
            throw new IllegalStateException(
                "现有 8080 后端同步失败，操作已回滚: " + ex.getMessage()
            );
        }
    }
}

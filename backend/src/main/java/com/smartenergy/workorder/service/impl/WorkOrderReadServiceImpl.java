package com.smartenergy.workorder.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartenergy.workorder.mapper.WorkOrderQueryMapper;
import com.smartenergy.workorder.service.WorkOrderReadService;
import com.smartenergy.workorder.vo.ActiveAssignmentVO;
import com.smartenergy.workorder.vo.WorkOrderAssignmentVO;
import com.smartenergy.workorder.vo.WorkOrderReadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderReadServiceImpl implements WorkOrderReadService {

    /**
     * 🟢 修复：用 SNAKE_CASE 命名策略 + 注册 JavaTimeModule
     * 1) PostgreSQL json_agg 输出 {"personnel_id": 2}，默认 Jackson 不做 snake_case → camelCase 转换，
     *    导致 ActiveAssignmentVO.personnelId 字段无法匹配，抛 UnrecognizedPropertyException。
     * 2) ActiveAssignmentVO.assignedAt 是 LocalDateTime，手动 new ObjectMapper() 不像 Spring Boot 自动注册
     *    JavaTimeModule，所以必须显式 registerModule(JavaTimeModule) 否则抛 InvalidDefinitionException。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final TypeReference<List<ActiveAssignmentVO>> ACTIVE_LIST_TYPE = new TypeReference<>() {};

    private final WorkOrderQueryMapper queryMapper;

    @Override
    public Page<WorkOrderReadVO> listOrders(String status, long pageNum, long pageSize) {
        long offset = Math.max(0, (pageNum - 1) * pageSize);
        String statusParam = StringUtils.hasText(status) ? status.toUpperCase() : null;

        List<WorkOrderReadVO> records = queryMapper.selectOrderList(statusParam, offset, pageSize);
        records.forEach(this::enrichActiveAssignments);
        long total = queryMapper.countOrderList(statusParam);

        Page<WorkOrderReadVO> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(records);
        return page;
    }

    @Override
    public WorkOrderReadVO getOrderDetail(Long id) {
        WorkOrderReadVO vo = queryMapper.selectOrderById(id);
        if (vo == null) {
            throw new IllegalArgumentException("工单不存在: id=" + id);
        }
        enrichActiveAssignments(vo);
        return vo;
    }

    @Override
    public List<WorkOrderAssignmentVO> getAssignments(Long workOrderId) {
        return queryMapper.selectAssignmentsByWorkOrder(workOrderId);
    }

    /** 把 SQL 聚合出的 activeAssignmentsJson 解析成 List<ActiveAssignmentVO> */
    private void enrichActiveAssignments(WorkOrderReadVO vo) {
        if (vo == null) return;
        String json = vo.getActiveAssignmentsJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            vo.setActiveAssignments(Collections.emptyList());
            vo.setAssigneeCount(0);
            return;
        }
        try {
            List<ActiveAssignmentVO> list = MAPPER.readValue(json, ACTIVE_LIST_TYPE);
            vo.setActiveAssignments(list);
            vo.setAssigneeCount(list == null ? 0 : list.size());
        } catch (Exception e) {
            log.warn("[WorkOrderRead] activeAssignments 解析失败: json={}", json, e);
            vo.setActiveAssignments(Collections.emptyList());
            vo.setAssigneeCount(0);
        }
    }
}

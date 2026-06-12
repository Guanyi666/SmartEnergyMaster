package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelMapper;
import com.smartenergy.backend.service.DispatchDashboardService;
import com.smartenergy.backend.service.MaintenancePersonnelService;
import com.smartenergy.backend.vo.DispatchBoardVO;
import com.smartenergy.backend.vo.DispatchSummaryVO;
import com.smartenergy.backend.vo.MaintenancePersonnelVO;
import com.smartenergy.backend.vo.SkillGroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchDashboardServiceImpl implements DispatchDashboardService {

    private final MaintenancePersonnelMapper personnelMapper;
    private final WorkOrderMapper workOrderMapper;
    private final MaintenancePersonnelService personnelService;
    private final JdbcTemplate jdbcTemplate;

    /** 已知技能白名单（与 SkillChip 配色保持一致） */
    private static final List<String> KNOWN_SKILLS = List.of("电气", "机械", "液压", "仪表", "自动化");

    @Override
    public DispatchSummaryVO summary() {
        DispatchSummaryVO vo = new DispatchSummaryVO();

        // ---- 工单统计 ----
        vo.setTotalOrders(workOrderMapper.selectCount(null));
        vo.setPendingOrders(workOrderMapper.selectCount(
                new QueryWrapper<WorkOrder>().eq("status", "PENDING")));
        vo.setInProgressOrders(workOrderMapper.selectCount(
                new QueryWrapper<WorkOrder>().eq("status", "IN_PROGRESS")));
        vo.setResolvedOrders(workOrderMapper.selectCount(
                new QueryWrapper<WorkOrder>().eq("status", "RESOLVED")));

        // ---- 人员统计 ----
        vo.setTotalPersonnel(personnelMapper.selectCount(null));
        vo.setOnDutyPersonnel(personnelMapper.selectCount(
                new QueryWrapper<MaintenancePersonnel>().eq("is_on_duty", true)));
        vo.setOffDutyPersonnel(personnelMapper.selectCount(
                new QueryWrapper<MaintenancePersonnel>().eq("is_on_duty", false)));

        // ---- 平均负载率 ----
        // 直接 SQL 算 AVG(current_workload*1.0/max_workload)*100，NULLIF 防空
        Double avgRate = jdbcTemplate.queryForObject(
                "SELECT AVG(CASE WHEN max_workload > 0 THEN current_workload * 100.0 / max_workload ELSE 0 END) " +
                "FROM workorder_maintenance_personnel WHERE is_on_duty = TRUE",
                Double.class);
        vo.setAvgWorkloadRate(avgRate == null ? 0 : (int) Math.round(avgRate));

        // ---- 技能覆盖 ----
        // ⚠️ 严重问题 #3 修复改用 String 后，这里改为：先把 specializations 是 JSON 字符串
        // 拉下来，应用层用 Jackson 解析后统计每个 skill 出现的人数。避免 LATERAL / jsonb_array
        // 隐式 JOIN 的 SQL 兼容坑。
        Map<String, Long> skillCoverage = new LinkedHashMap<>();
        for (String skill : KNOWN_SKILLS) {
            skillCoverage.put(skill, 0L);
        }
        try {
            List<MaintenancePersonnel> allOnDuty = personnelMapper.selectList(
                    new QueryWrapper<MaintenancePersonnel>().eq("is_on_duty", true));
            for (MaintenancePersonnel p : allOnDuty) {
                List<String> skills = parseSpecializations(p.getSpecializations());
                for (String s : skills) {
                    skillCoverage.merge(s, 1L, Long::sum);
                }
            }
        } catch (Exception ex) {
            log.warn("[Dashboard] 技能覆盖计算失败", ex);
        }
        vo.setSkillCoverage(skillCoverage);

        return vo;
    }

    private static List<String> parseSpecializations(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public DispatchBoardVO board() {
        DispatchBoardVO vo = new DispatchBoardVO();

        // 查所有在岗人员
        List<MaintenancePersonnel> all = personnelMapper.selectList(
                new QueryWrapper<MaintenancePersonnel>().eq("is_on_duty", true)
                        .orderByAsc("skill_level", "employee_no"));

        // 按技能分组
        Map<String, List<MaintenancePersonnel>> grouped = new LinkedHashMap<>();
        for (String skill : KNOWN_SKILLS) {
            grouped.put(skill, new ArrayList<>());
        }
        for (MaintenancePersonnel p : all) {
            // specializations 现在是 String JSON，应用层解析
            List<String> skills = parseSpecializations(p.getSpecializations());
            for (String s : skills) {
                grouped.computeIfAbsent(s, k -> new ArrayList<>()).add(p);
            }
        }

        List<SkillGroupVO> groupVOs = new ArrayList<>();
        for (Map.Entry<String, List<MaintenancePersonnel>> e : grouped.entrySet()) {
            SkillGroupVO g = new SkillGroupVO();
            g.setSkill(e.getKey());
            g.setPersonnelCount((long) e.getValue().size());
            int totalCur = 0, totalMax = 0;
            List<MaintenancePersonnelVO> personnelVOs = new ArrayList<>();
            for (MaintenancePersonnel p : e.getValue()) {
                totalCur += p.getCurrentWorkload() == null ? 0 : p.getCurrentWorkload();
                totalMax += p.getMaxWorkload() == null ? 0 : p.getMaxWorkload();
                personnelVOs.add(personnelService.toVO(p));
            }
            g.setTotalCurrentWorkload(totalCur);
            g.setTotalMaxWorkload(totalMax);
            g.setAvgWorkloadRate(totalMax == 0 ? 0
                    : (int) Math.round(totalCur * 100.0 / totalMax));
            g.setPersonnel(personnelVOs);
            groupVOs.add(g);
        }
        vo.setSkillGroups(groupVOs);

        vo.setOffDutyCount(personnelMapper.selectCount(
                new QueryWrapper<MaintenancePersonnel>().eq("is_on_duty", false)));

        return vo;
    }
}

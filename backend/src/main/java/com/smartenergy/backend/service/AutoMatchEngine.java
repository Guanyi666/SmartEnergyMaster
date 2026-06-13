package com.smartenergy.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.entity.MaintenancePersonnelArchive;
import com.smartenergy.backend.mapper.MaintenancePersonnelArchiveMapper;
import com.smartenergy.backend.vo.MatchCandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 自动匹配引擎（Step 5 核心）
 *
 * 🔴 严重问题 #2 修复：faultType 用英文常量，与现有 SensorDataServiceImpl.java:82,94 对齐
 * 现有 faultType 取值：MECHANICAL_JAM、COOLING_INTERRUPT
 * 未来扩展：ELECTRICAL_OVERLOAD、SENSOR_DRIFT
 *
 * 匹配评分公式：
 *   基础分 50
 * + 技能匹配：每个匹配 +15（最多 +30）
 * + 技能等级 bonus：JUNIOR +0 / INTERMEDIATE +5 / SENIOR +10 / EXPERT +15
 * - 负载率 * 20 = currentWorkload / maxWorkload * 20
 *
 * 最低分 0，最高分 100（基础50+技能30+等级15=95，封顶95或按公式计算）
 */
@Component
@RequiredArgsConstructor
public class AutoMatchEngine {

    private final MaintenancePersonnelArchiveMapper archiveMapper;

    private static final Map<String, List<String>> FAULT_TO_SKILL = Map.ofEntries(
        Map.entry("MECHANICAL_JAM",     List.of("机械", "液压")),
        Map.entry("COOLING_INTERRUPT",  List.of("液压", "仪表")),
        Map.entry("ELECTRICAL_OVERLOAD",List.of("电气", "自动化")),
        Map.entry("SENSOR_DRIFT",       List.of("仪表", "自动化"))
    );

    private static final Map<String, Integer> LEVEL_BONUS = Map.of(
        "JUNIOR", 0,
        "INTERMEDIATE", 5,
        "SENIOR", 10,
        "EXPERT", 15
    );

    /**
     * 解析故障类型 → 所需技能列表
     * 用 contains 模糊匹配，兼容后端 faultType 带前后缀的情况
     */
    public List<String> resolveRequiredSkills(String faultType) {
        if (faultType == null || faultType.isBlank()) return List.of();
        return FAULT_TO_SKILL.entrySet().stream()
                .filter(e -> faultType.contains(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(List.of());
    }

    /**
     * 给定一个候选人 + 所需技能列表，计算匹配分
     * @return 0-100 的整数
     */
    public int scorePersonnel(MaintenancePersonnel p, List<String> requiredSkills) {
        if (p == null) return 0;

        // 1. 基础分
        int score = 50;

        // v4: 从 archive 表读 specializations/skillLevel
        MaintenancePersonnelArchive archive = archiveMapper.selectOne(
                new QueryWrapper<MaintenancePersonnelArchive>().eq("employee_no", p.getEmployeeNo()));

        // 2. 技能匹配（specializations 改 String 后先解析）
        int skillBonus = 0;
        List<String> personSkills = parseSkills(archive == null ? null : archive.getSpecializations());
        if (!personSkills.isEmpty() && !requiredSkills.isEmpty()) {
            for (String required : requiredSkills) {
                if (personSkills.stream().anyMatch(s -> s.equals(required))) {
                    skillBonus += 15;
                }
            }
            skillBonus = Math.min(skillBonus, 30);
        }
        score += skillBonus;

        // 3. 技能等级 bonus
        String skillLevel = archive == null ? null : archive.getSkillLevel();
        if (skillLevel != null) {
            score += LEVEL_BONUS.getOrDefault(skillLevel, 0);
        }

        // 4. 负载惩罚
        if (p.getMaxWorkload() != null && p.getMaxWorkload() > 0
                && p.getCurrentWorkload() != null) {
            double loadRate = p.getCurrentWorkload() * 1.0 / p.getMaxWorkload();
            score -= (int) Math.round(loadRate * 20);
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * 排序候选人并返回 Top N + 命中的技能列表
     */
    public List<MatchCandidateVO> rankCandidates(List<MaintenancePersonnel> personnel,
                                                  List<String> requiredSkills,
                                                  int topN) {
        return personnel.stream()
                .map(p -> {
                    // v4: 从 archive 读档案字段
                    MaintenancePersonnelArchive archive = archiveMapper.selectOne(
                            new QueryWrapper<MaintenancePersonnelArchive>().eq("employee_no", p.getEmployeeNo()));
                    String name = archive == null ? null : archive.getName();
                    String specializationsJson = archive == null ? null : archive.getSpecializations();
                    String skillLevel = archive == null ? null : archive.getSkillLevel();

                    MatchCandidateVO vo = new MatchCandidateVO();
                    vo.setPersonnelId(p.getId());
                    vo.setEmployeeNo(p.getEmployeeNo());
                    vo.setName(name);
                    vo.setAvatarColor(p.getAvatarColor());
                    vo.setSpecializations(parseSkills(specializationsJson));
                    vo.setSkillLevel(skillLevel);
                    vo.setCurrentWorkload(p.getCurrentWorkload());
                    vo.setMaxWorkload(p.getMaxWorkload());
                    vo.setWorkloadRate(calcWorkloadRate(p));
                    vo.setMatchScore(scorePersonnel(p, requiredSkills));
                    vo.setMatchedSkills(matchedSkills(p, requiredSkills));
                    return vo;
                })
                .filter(vo -> vo.getMatchScore() > 0)
                .sorted((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()))
                .limit(topN > 0 ? topN : 5)
                .collect(Collectors.toList());
    }

    private List<String> matchedSkills(MaintenancePersonnel p, List<String> required) {
        if (p == null || required == null) return List.of();
        MaintenancePersonnelArchive archive = archiveMapper.selectOne(
                new QueryWrapper<MaintenancePersonnelArchive>().eq("employee_no", p.getEmployeeNo()));
        List<String> personSkills = parseSkills(archive == null ? null : archive.getSpecializations());
        if (personSkills.isEmpty()) return List.of();
        List<String> matched = new ArrayList<>();
        for (String r : required) {
            if (personSkills.stream().anyMatch(s -> Objects.equals(s, r))) {
                matched.add(r);
            }
        }
        return matched;
    }

    /** 解析 JSON 字符串 → List<String>（specializations 现在是 String） */
    private List<String> parseSkills(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private int calcWorkloadRate(MaintenancePersonnel p) {
        if (p.getMaxWorkload() == null || p.getMaxWorkload() == 0) return 0;
        int cw = p.getCurrentWorkload() == null ? 0 : p.getCurrentWorkload();
        return (int) Math.round(cw * 100.0 / p.getMaxWorkload());
    }
}

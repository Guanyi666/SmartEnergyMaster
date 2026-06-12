package com.smartenergy.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.entity.MaintenanceSOP;
import com.smartenergy.backend.entity.RepairCase;
import com.smartenergy.backend.mapper.MaintenanceSOPMapper;
import com.smartenergy.backend.mapper.MaintenanceSOPRequiredPartMapper;
import com.smartenergy.backend.mapper.MaintenanceSOPStepMapper;
import com.smartenergy.backend.mapper.RepairCaseMapper;
import com.smartenergy.backend.service.impl.MaintenanceSOPServiceImpl;
import com.smartenergy.backend.service.impl.RepairCaseServiceImpl;
import com.smartenergy.backend.vo.CaseDetailVO;
import com.smartenergy.backend.vo.SOPDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Epic 7 核心匹配/评分算法的纯逻辑单元测试，不依赖数据库。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeMatchingTest {

    @Mock
    private MaintenanceSOPMapper sopMapper;
    // v6.2 改造后 MaintenanceSOPServiceImpl 新增的 2 个 mapper（拆 JSON 子表）
    @Mock
    private MaintenanceSOPStepMapper sopStepMapper;
    @Mock
    private MaintenanceSOPRequiredPartMapper sopRequiredPartMapper;
    @Mock
    private RepairCaseMapper caseMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MaintenanceSOPServiceImpl sopService() {
        return new MaintenanceSOPServiceImpl(sopMapper, sopStepMapper, sopRequiredPartMapper, objectMapper);
    }

    private RepairCaseServiceImpl caseService() {
        return new RepairCaseServiceImpl(caseMapper, null, null);
    }

    private MaintenanceSOP sop(Long id, String code, String deviceType, String faultType, int version) {
        MaintenanceSOP s = new MaintenanceSOP();
        s.setId(id);
        s.setSopCode(code);
        s.setDeviceType(deviceType);
        s.setFaultType(faultType);
        s.setTitle(code);
        s.setContent("# " + code);
        // v6.2 改造后：steps/requiredParts 已迁出到子表，这里不再 setSteps/setRequiredParts
        s.setRequiredSkills("[]");
        s.setRequiredTools("[]");
        s.setEstimatedMinutes(60);
        s.setVersion(version);
        s.setIsActive(true);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    private RepairCase repairCase(Long id, String code, String deviceType, String faultType, String keywords, String rootCause) {
        RepairCase c = new RepairCase();
        c.setId(id);
        c.setCaseCode(code);
        c.setTitle(code);
        c.setDeviceType(deviceType);
        c.setFaultType(faultType);
        c.setFaultSymptom("symptom");
        c.setRootCause(rootCause);
        c.setRepairProcess("process");
        c.setRepairResult("OK");
        c.setKeywords(keywords);
        c.setTechnician("张工");
        c.setDurationMinutes(60);
        c.setOccurredAt(LocalDateTime.now());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    // ---------- SOP 匹配 ----------

    @Test
    void sopMatch_prefersExactDeviceAndFaultOverFaultOnly() {
        MaintenanceSOP exact = sop(1L, "SOP-EXACT", "ARC_FURNACE", "MECHANICAL_JAM", 1);
        MaintenanceSOP faultOnly = sop(2L, "SOP-FAULT", "PUMP", "MECHANICAL_JAM", 1);
        when(sopMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(faultOnly, exact));

        SOPDetailVO match = sopService().findBestMatch("ARC_FURNACE", "MECHANICAL_JAM");
        assertNotNull(match);
        assertEquals(1L, match.getId(), "设备+故障精确匹配的 SOP 应优先");
    }

    @Test
    void sopMatch_higherVersionWinsWhenScoreTied() {
        MaintenanceSOP older = sop(10L, "SOP-OLD", "ARC_FURNACE", "MECHANICAL_JAM", 1);
        MaintenanceSOP newer = sop(11L, "SOP-NEW", "ARC_FURNACE", "MECHANICAL_JAM", 3);
        when(sopMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(older, newer));

        SOPDetailVO match = sopService().findBestMatch("ARC_FURNACE", "MECHANICAL_JAM");
        assertEquals(11L, match.getId(), "同分时版本号更高者优先");
    }

    @Test
    void sopMatch_returnsNullWhenNoCandidates() {
        when(sopMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        assertNull(sopService().findBestMatch("ARC_FURNACE", "UNKNOWN_FAULT"));
    }

    @Test
    void sopMatchId_returnsNullForEmptyInput() {
        assertNull(sopService().matchSOPId("", ""));
        assertNull(sopService().matchSOPId(null, null));
    }

    // ---------- 案例相似度 ----------

    @Test
    void caseSimilarity_exactMatchScoresHigher() {
        RepairCase exact = repairCase(1L, "RC-1", "ARC_FURNACE", "MECHANICAL_JAM", "振动,卡涩", "轴承磨损");
        RepairCase partial = repairCase(2L, "RC-2", "PUMP", "MECHANICAL_JAM", "泄漏", "密封老化");
        when(caseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(partial, exact));

        List<CaseDetailVO> result = caseService().findSimilar("ARC_FURNACE", "MECHANICAL_JAM", "振动", 5);
        assertFalse(result.isEmpty());
        assertEquals("RC-1", result.get(0).getCaseCode(), "设备类型+故障类型+关键词三重命中应排第一");
    }

    @Test
    void caseSimilarity_keywordBoostAppliesOnTopOfTypeMatch() {
        RepairCase typeOnly = repairCase(1L, "RC-TYPE", "ARC_FURNACE", "MECHANICAL_JAM", "其他", "其他原因");
        RepairCase typePlusKw = repairCase(2L, "RC-TYPE-KW", "ARC_FURNACE", "MECHANICAL_JAM", "振动,卡涩", "轴承");
        when(caseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(typeOnly, typePlusKw));

        List<CaseDetailVO> result = caseService().findSimilar("ARC_FURNACE", "MECHANICAL_JAM", "振动", 5);
        assertEquals(2, result.size());
        assertEquals("RC-TYPE-KW", result.get(0).getCaseCode(), "关键词命中应让排序更靠前");
    }

    @Test
    void caseSimilarity_unrelatedCaseExcluded() {
        RepairCase unrelated = repairCase(1L, "RC-X", "PUMP", "PIPE_BLOCK", "完全无关", "完全无关");
        RepairCase relevant = repairCase(2L, "RC-Y", "ARC_FURNACE", "MECHANICAL_JAM", "卡涩", "轴承");
        when(caseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(unrelated, relevant));

        List<CaseDetailVO> result = caseService().findSimilar("ARC_FURNACE", "MECHANICAL_JAM", null, 5);
        assertEquals(1, result.size(), "无关案例应被过滤");
        assertEquals("RC-Y", result.get(0).getCaseCode());
    }

    @Test
    void caseSimilarity_respectsLimit() {
        when(caseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                repairCase(1L, "RC-1", "ARC_FURNACE", "MECHANICAL_JAM", "k", "c"),
                repairCase(2L, "RC-2", "ARC_FURNACE", "MECHANICAL_JAM", "k", "c"),
                repairCase(3L, "RC-3", "ARC_FURNACE", "MECHANICAL_JAM", "k", "c"),
                repairCase(4L, "RC-4", "ARC_FURNACE", "MECHANICAL_JAM", "k", "c")
        ));
        List<CaseDetailVO> result = caseService().findSimilar("ARC_FURNACE", "MECHANICAL_JAM", null, 2);
        assertEquals(2, result.size(), "limit 参数应生效");
    }
}
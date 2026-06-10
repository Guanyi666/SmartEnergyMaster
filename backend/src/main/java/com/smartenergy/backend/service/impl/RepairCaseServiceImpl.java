package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.CaseCreateRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.RepairCase;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.RepairCaseMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.service.RepairCaseService;
import com.smartenergy.backend.vo.CaseDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairCaseServiceImpl implements RepairCaseService {

    private final RepairCaseMapper caseMapper;
    private final WorkOrderMapper workOrderMapper;
    private final DeviceMapper deviceMapper;

    @Override
    public List<CaseDetailVO> listCases(String deviceType, String faultType, String keyword) {
        QueryWrapper<RepairCase> wrapper = new QueryWrapper<RepairCase>().orderByDesc("occurred_at");
        if (StringUtils.hasText(deviceType)) {
            wrapper.eq("device_type", deviceType);
        }
        if (StringUtils.hasText(faultType)) {
            wrapper.eq("fault_type", faultType);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("title", keyword).or().like("fault_symptom", keyword).or().like("root_cause", keyword).or().like("keywords", keyword));
        }
        return caseMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public CaseDetailVO getDetail(Long id) {
        return toVO(requireCase(id));
    }

    @Override
    @Transactional
    public CaseDetailVO createCase(CaseCreateRequest request) {
        RepairCase entity = new RepairCase();
        BeanUtils.copyProperties(request, entity);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getOccurredAt() == null) {
            entity.setOccurredAt(now);
        }
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        caseMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public CaseDetailVO createCaseFromWorkOrder(Long workOrderId, String title, String rootCause, String repairProcess, String technician) {
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在: " + workOrderId);
        }
        Device device = deviceMapper.selectById(order.getDeviceId());
        Integer durationMinutes = null;
        if (order.getAcceptedAt() != null && order.getResolvedAt() != null) {
            durationMinutes = (int) Duration.between(order.getAcceptedAt(), order.getResolvedAt()).toMinutes();
        }
        RepairCase entity = new RepairCase();
        entity.setCaseCode(generateCaseCode());
        entity.setTitle(StringUtils.hasText(title) ? title : order.getTitle() + " 维修复盘");
        entity.setDeviceType(device == null ? "" : device.getDeviceType());
        entity.setFaultType(order.getFaultType());
        entity.setFaultSymptom(order.getDescription());
        entity.setRootCause(rootCause);
        entity.setRepairProcess(repairProcess);
        entity.setRepairResult("已闭环");
        entity.setDurationMinutes(durationMinutes);
        entity.setTechnician(technician);
        entity.setKeywords(joinKeywords(device, order));
        entity.setRelatedWorkOrderId(order.getId());
        entity.setOccurredAt(order.getResolvedAt() == null ? LocalDateTime.now() : order.getResolvedAt());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        caseMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public List<CaseDetailVO> findSimilar(String deviceType, String faultType, String keyword, int limit) {
        List<RepairCase> candidates = caseMapper.selectList(new QueryWrapper<RepairCase>()
                .eq(StringUtils.hasText(deviceType), "device_type", deviceType)
                .eq(StringUtils.hasText(faultType), "fault_type", faultType)
                .orderByDesc("occurred_at")
                .last("LIMIT 50"));
        Set<String> keywordSet = splitKeywords(keyword);
        return candidates.stream()
                .map(c -> new ScoredCase(c, score(c, deviceType, faultType, keywordSet)))
                .filter(sc -> sc.score > 0)
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(Math.max(1, limit))
                .map(sc -> toVO(sc.entity))
                .toList();
    }

    @Override
    @Transactional
    public CaseDetailVO updateCase(Long id, CaseCreateRequest request) {
        RepairCase entity = requireCase(id);
        BeanUtils.copyProperties(request, entity, "id", "createdAt");
        entity.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void deleteCase(Long id) {
        requireCase(id);
        caseMapper.deleteById(id);
    }

    private int score(RepairCase entity, String deviceType, String faultType, Set<String> keywordSet) {
        int s = 0;
        if (StringUtils.hasText(deviceType) && deviceType.equalsIgnoreCase(entity.getDeviceType())) {
            s += 3;
        }
        if (StringUtils.hasText(faultType) && faultType.equalsIgnoreCase(entity.getFaultType())) {
            s += 2;
        }
        if (!keywordSet.isEmpty()) {
            Set<String> caseWords = splitKeywords(entity.getKeywords());
            caseWords.addAll(splitKeywords(entity.getTitle()));
            caseWords.addAll(splitKeywords(entity.getFaultSymptom()));
            for (String w : keywordSet) {
                if (caseWords.contains(w)) {
                    s += 1;
                }
            }
        }
        return s;
    }

    private Set<String> splitKeywords(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,，\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String joinKeywords(Device device, WorkOrder order) {
        StringBuilder sb = new StringBuilder();
        if (device != null && StringUtils.hasText(device.getDeviceType())) {
            sb.append(device.getDeviceType());
        }
        if (order.getFaultType() != null) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(order.getFaultType());
        }
        return sb.toString();
    }

    private String generateCaseCode() {
        return "RC-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private RepairCase requireCase(Long id) {
        RepairCase entity = caseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("维修案例不存在: " + id);
        }
        return entity;
    }

    private CaseDetailVO toVO(RepairCase entity) {
        CaseDetailVO vo = new CaseDetailVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private record ScoredCase(RepairCase entity, int score) {
    }
}
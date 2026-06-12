package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.smartenergy.backend.dto.SOPCreateRequest;
import com.smartenergy.backend.dto.SOPUpdateRequest;
import com.smartenergy.backend.entity.MaintenanceSOP;
import com.smartenergy.backend.mapper.MaintenanceSOPMapper;
import com.smartenergy.backend.service.MaintenanceSOPService;
import com.smartenergy.backend.vo.SOPDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceSOPServiceImpl implements MaintenanceSOPService {

    private final MaintenanceSOPMapper sopMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SOPDetailVO> listSOPs(String deviceType, String faultType, String keyword) {
        QueryWrapper<MaintenanceSOP> wrapper = new QueryWrapper<MaintenanceSOP>()
                .eq("is_active", true)
                .orderByDesc("updated_at");
        if (StringUtils.hasText(deviceType)) {
            wrapper.eq("device_type", deviceType);
        }
        if (StringUtils.hasText(faultType)) {
            wrapper.eq("fault_type", faultType);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("title", keyword).or().like("summary", keyword).or().like("content", keyword));
        }
        return sopMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public SOPDetailVO getDetail(Long id) {
        MaintenanceSOP sop = requireSOP(id);
        return toVO(sop);
    }

    @Override
    @Transactional
    public SOPDetailVO createSOP(SOPCreateRequest request) {
        MaintenanceSOP sop = new MaintenanceSOP();
        BeanUtils.copyProperties(request, sop, "steps", "requiredSkills", "requiredTools", "requiredParts");
        sop.setSteps(writeJsonArray(request.getSteps()));
        sop.setRequiredSkills(writeJsonArray(request.getRequiredSkills()));
        sop.setRequiredTools(writeJsonArray(request.getRequiredTools()));
        sop.setRequiredParts(writeJsonArray(request.getRequiredParts()));
        sop.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        sop.setVersion(1);
        LocalDateTime now = LocalDateTime.now();
        sop.setCreatedAt(now);
        sop.setUpdatedAt(now);
        sopMapper.insert(sop);
        return toVO(sop);
    }

    @Override
    @Transactional
    public SOPDetailVO updateSOP(Long id, SOPUpdateRequest request) {
        MaintenanceSOP sop = requireSOP(id);
        BeanUtils.copyProperties(request, sop, "steps", "requiredSkills", "requiredTools", "requiredParts");
        sop.setSteps(writeJsonArray(request.getSteps()));
        sop.setRequiredSkills(writeJsonArray(request.getRequiredSkills()));
        sop.setRequiredTools(writeJsonArray(request.getRequiredTools()));
        sop.setRequiredParts(writeJsonArray(request.getRequiredParts()));
        if (request.getIsActive() != null) {
            sop.setIsActive(request.getIsActive());
        }
        if (request.getEstimatedMinutes() != null) {
            sop.setEstimatedMinutes(request.getEstimatedMinutes());
        }
        sop.setVersion(sop.getVersion() + 1);
        sop.setUpdatedAt(LocalDateTime.now());
        sopMapper.updateById(sop);
        return toVO(sop);
    }

    @Override
    @Transactional
    public void deleteSOP(Long id) {
        requireSOP(id);
        sopMapper.deleteById(id);
    }

    @Override
    public Long matchSOPId(String deviceType, String faultType) {
        SOPDetailVO match = findBestMatch(deviceType, faultType);
        return match == null ? null : match.getId();
    }

    @Override
    public SOPDetailVO findBestMatch(String deviceType, String faultType) {
        if (!StringUtils.hasText(faultType)) {
            return null;
        }
        List<MaintenanceSOP> candidates = sopMapper.selectList(new QueryWrapper<MaintenanceSOP>()
                .eq("is_active", true)
                .and(w -> w.eq("fault_type", faultType).or().eq("device_type", deviceType == null ? "" : deviceType)));
        if (candidates.isEmpty()) {
            return null;
        }
        Optional<MaintenanceSOP> best = candidates.stream()
                .max(Comparator.comparingInt(sop -> score(sop, deviceType, faultType)));
        return best.map(this::toVO).orElse(null);
    }

    private int score(MaintenanceSOP sop, String deviceType, String faultType) {
        int s = 0;
        if (StringUtils.hasText(deviceType) && deviceType.equalsIgnoreCase(sop.getDeviceType())) {
            s += 5;
        }
        if (faultType != null && faultType.equalsIgnoreCase(sop.getFaultType())) {
            s += 3;
        }
        if (sop.getVersion() != null) {
            s += Math.min(sop.getVersion(), 3);
        }
        return s;
    }

    private MaintenanceSOP requireSOP(Long id) {
        MaintenanceSOP sop = sopMapper.selectById(id);
        if (sop == null) {
            throw new IllegalArgumentException("SOP 不存在: " + id);
        }
        return sop;
    }

    private SOPDetailVO toVO(MaintenanceSOP sop) {
        SOPDetailVO vo = new SOPDetailVO();
        BeanUtils.copyProperties(sop, vo, "steps", "requiredSkills", "requiredTools", "requiredParts");
        vo.setSteps(parseJson(sop.getSteps()));
        vo.setRequiredSkills(parseJson(sop.getRequiredSkills()));
        vo.setRequiredTools(parseJson(sop.getRequiredTools()));
        vo.setRequiredParts(parseJson(sop.getRequiredParts()));
        return vo;
    }

    private String writeJsonArray(List<String> values) {
        if (values == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return MissingNode.getInstance();
        }
    }
}
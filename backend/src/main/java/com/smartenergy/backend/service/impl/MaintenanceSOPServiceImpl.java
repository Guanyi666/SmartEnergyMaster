package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.smartenergy.backend.dto.SOPCreateRequest;
import com.smartenergy.backend.dto.SOPUpdateRequest;
import com.smartenergy.backend.entity.MaintenanceSOP;
import com.smartenergy.backend.entity.MaintenanceSOPRequiredPart;
import com.smartenergy.backend.entity.MaintenanceSOPStep;
import com.smartenergy.backend.mapper.MaintenanceSOPMapper;
import com.smartenergy.backend.mapper.MaintenanceSOPRequiredPartMapper;
import com.smartenergy.backend.mapper.MaintenanceSOPStepMapper;
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

/**
 * v4 改造：步骤/备件改读子表 maintenance_sop_step / maintenance_sop_required_part
 * （不再存 JSON 在 maintenance_sop.steps / required_parts）
 */
@Service
@RequiredArgsConstructor
public class MaintenanceSOPServiceImpl implements MaintenanceSOPService {

    private final MaintenanceSOPMapper sopMapper;
    private final MaintenanceSOPStepMapper stepMapper;
    private final MaintenanceSOPRequiredPartMapper requiredPartMapper;
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
        BeanUtils.copyProperties(request, sop, "steps", "requiredSkills", "requiredTools", "requiredParts", "steps", "requiredPartCodes");
        sop.setRequiredSkills(writeJsonArray(request.getRequiredSkills()));
        sop.setRequiredTools(writeJsonArray(request.getRequiredTools()));
        sop.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        sop.setVersion(1);
        LocalDateTime now = LocalDateTime.now();
        sop.setCreatedAt(now);
        sop.setUpdatedAt(now);
        sopMapper.insert(sop);

        // v4: 写子表
        replaceSteps(sop.getId(), request.getSteps());
        replaceRequiredParts(sop.getId(), request.getRequiredParts());

        return toVO(sop);
    }

    @Override
    @Transactional
    public SOPDetailVO updateSOP(Long id, SOPUpdateRequest request) {
        MaintenanceSOP sop = requireSOP(id);
        BeanUtils.copyProperties(request, sop, "steps", "requiredSkills", "requiredTools", "requiredParts", "steps", "requiredPartCodes");
        sop.setRequiredSkills(writeJsonArray(request.getRequiredSkills()));
        sop.setRequiredTools(writeJsonArray(request.getRequiredTools()));
        if (request.getIsActive() != null) {
            sop.setIsActive(request.getIsActive());
        }
        if (request.getEstimatedMinutes() != null) {
            sop.setEstimatedMinutes(request.getEstimatedMinutes());
        }
        sop.setVersion(sop.getVersion() + 1);
        sop.setUpdatedAt(LocalDateTime.now());
        sopMapper.updateById(sop);

        // v4: 重写子表
        if (request.getSteps() != null) {
            replaceSteps(sop.getId(), request.getSteps());
        }
        if (request.getRequiredParts() != null) {
            replaceRequiredParts(sop.getId(), request.getRequiredParts());
        }
        return toVO(sop);
    }

    @Override
    @Transactional
    public void deleteSOP(Long id) {
        requireSOP(id);
        // ON DELETE CASCADE 会自动删除子表
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

    /** v4: 替换 SOP 步骤子表 */
    private void replaceSteps(Long sopId, List<String> steps) {
        stepMapper.delete(new QueryWrapper<MaintenanceSOPStep>().eq("sop_id", sopId));
        if (steps == null) return;
        int stepNo = 1;
        LocalDateTime now = LocalDateTime.now();
        for (String s : steps) {
            if (!StringUtils.hasText(s)) continue;
            MaintenanceSOPStep step = new MaintenanceSOPStep();
            step.setSopId(sopId);
            step.setStepNo(stepNo++);
            step.setTitle("步骤 " + (stepNo - 1));
            step.setContent(s);
            step.setCreatedAt(now);
            step.setUpdatedAt(now);
            stepMapper.insert(step);
        }
    }

    /** v4: 替换 SOP 所需备件子表 */
    private void replaceRequiredParts(Long sopId, List<String> partCodes) {
        requiredPartMapper.delete(new QueryWrapper<MaintenanceSOPRequiredPart>().eq("sop_id", sopId));
        if (partCodes == null) return;
        LocalDateTime now = LocalDateTime.now();
        for (String code : partCodes) {
            if (!StringUtils.hasText(code)) continue;
            MaintenanceSOPRequiredPart req = new MaintenanceSOPRequiredPart();
            req.setSopId(sopId);
            req.setPartCode(code.trim());
            req.setQuantity(1);
            req.setIsMandatory(true);
            req.setCreatedAt(now);
            req.setUpdatedAt(now);
            requiredPartMapper.insert(req);
        }
    }

    private SOPDetailVO toVO(MaintenanceSOP sop) {
        SOPDetailVO vo = new SOPDetailVO();
        BeanUtils.copyProperties(sop, vo, "steps", "requiredSkills", "requiredTools", "requiredParts");
        // v4: 步骤从子表读
        List<MaintenanceSOPStep> steps = stepMapper.selectList(new QueryWrapper<MaintenanceSOPStep>()
                .eq("sop_id", sop.getId())
                .orderByAsc("step_no"));
        vo.setSteps(steps.stream().map(MaintenanceSOPStep::getContent).collect(java.util.stream.Collectors.toList()));
        // v4: 所需备件从子表读
        List<MaintenanceSOPRequiredPart> parts = requiredPartMapper.selectList(new QueryWrapper<MaintenanceSOPRequiredPart>()
                .eq("sop_id", sop.getId()));
        vo.setRequiredParts(parts.stream().map(p -> p.getPartCode()).collect(java.util.stream.Collectors.toList()));
        // requiredSkills/requiredTools 仍从主表的 JSON 字段读
        vo.setRequiredSkills(parseJson(sop.getRequiredSkills()));
        vo.setRequiredTools(parseJson(sop.getRequiredTools()));
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

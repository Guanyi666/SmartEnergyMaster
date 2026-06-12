package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.dto.SparePartCreateRequest;
import com.smartenergy.backend.dto.SparePartUsageRequest;
import com.smartenergy.backend.entity.MaintenanceSOP;
import com.smartenergy.backend.entity.SparePart;
import com.smartenergy.backend.entity.SparePartUsage;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.mapper.MaintenanceSOPMapper;
import com.smartenergy.backend.mapper.SparePartMapper;
import com.smartenergy.backend.mapper.SparePartUsageMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.service.SparePartService;
import com.smartenergy.backend.vo.SparePartUsageVO;
import com.smartenergy.backend.vo.SparePartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SparePartServiceImpl implements SparePartService {

    private final SparePartMapper partMapper;
    private final SparePartUsageMapper usageMapper;
    private final WorkOrderMapper workOrderMapper;
    private final MaintenanceSOPMapper sopMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SparePartVO> listParts(String keyword, Boolean lowStockOnly) {
        QueryWrapper<SparePart> wrapper = new QueryWrapper<SparePart>().orderByAsc("part_code");
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("part_code", keyword)
                    .or().like("name", keyword)
                    .or().like("spec", keyword)
                    .or().like("supplier", keyword));
        }
        List<SparePart> all = partMapper.selectList(wrapper);
        return all.stream()
                .map(this::toVO)
                .filter(vo -> lowStockOnly == null || !Boolean.TRUE.equals(lowStockOnly) || Boolean.TRUE.equals(vo.getLowStock()))
                .toList();
    }

    @Override
    public SparePartVO getPart(Long id) {
        return toVO(requirePart(id));
    }

    @Override
    @Transactional
    public SparePartVO createPart(SparePartCreateRequest request) {
        if (partMapper.exists(new QueryWrapper<SparePart>().eq("part_code", request.getPartCode()))) {
            throw new IllegalArgumentException("备件编号已存在: " + request.getPartCode());
        }
        SparePart entity = new SparePart();
        BeanUtils.copyProperties(request, entity);
        if (!StringUtils.hasText(entity.getUnit())) {
            entity.setUnit("件");
        }
        if (entity.getQuantity() == null) {
            entity.setQuantity(0);
        }
        if (entity.getSafetyStock() == null) {
            entity.setSafetyStock(0);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        partMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public SparePartVO updatePart(Long id, SparePartCreateRequest request) {
        SparePart entity = requirePart(id);
        if (!entity.getPartCode().equals(request.getPartCode())
                && partMapper.exists(new QueryWrapper<SparePart>().eq("part_code", request.getPartCode()).ne("id", id))) {
            throw new IllegalArgumentException("备件编号已存在: " + request.getPartCode());
        }
        BeanUtils.copyProperties(request, entity, "createdAt");
        entity.setUpdatedAt(LocalDateTime.now());
        partMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void deletePart(Long id) {
        requirePart(id);
        partMapper.deleteById(id);
    }

    @Override
    @Transactional
    public SparePartUsageVO recordUsage(SparePartUsageRequest request) {
        SparePart part = requirePart(request.getPartId());
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("领用数量必须大于 0");
        }
        if (part.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("库存不足：当前 " + part.getQuantity() + " " + part.getUnit() + "，申请 " + request.getQuantity());
        }
        // 扣减库存
        part.setQuantity(part.getQuantity() - request.getQuantity());
        part.setUpdatedAt(LocalDateTime.now());
        partMapper.updateById(part);

        // 写领用记录
        SparePartUsage usage = new SparePartUsage();
        usage.setPartId(request.getPartId());
        usage.setWorkOrderId(request.getWorkOrderId());
        usage.setQuantity(request.getQuantity());
        usage.setUserName(request.getUserName());
        usage.setNote(request.getNote());
        usage.setUsedAt(LocalDateTime.now());
        usage.setCreatedAt(LocalDateTime.now());
        usageMapper.insert(usage);

        return toUsageVO(usage, part);
    }

    @Override
    public List<SparePartUsageVO> listUsages(Long partId, int limit) {
        QueryWrapper<SparePartUsage> wrapper = new QueryWrapper<SparePartUsage>()
                .orderByDesc("used_at")
                .last("LIMIT " + Math.max(1, limit));
        if (partId != null) {
            wrapper.eq("part_id", partId);
        }
        return usageMapper.selectList(wrapper).stream()
                .map(u -> toUsageVO(u, partMapper.selectById(u.getPartId())))
                .toList();
    }

    @Override
    public List<SparePartUsageVO> listUsagesByWorkOrder(Long workOrderId) {
        if (workOrderId == null) {
            return List.of();
        }
        List<SparePartUsage> usages = usageMapper.selectList(new QueryWrapper<SparePartUsage>()
                .eq("work_order_id", workOrderId)
                .orderByDesc("used_at"));
        return usages.stream()
                .map(u -> toUsageVO(u, partMapper.selectById(u.getPartId())))
                .toList();
    }

    @Override
    @Transactional
    public List<SparePartUsageVO> autoDeductForWorkOrder(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getSopId() == null) {
            return List.of();
        }
        MaintenanceSOP sop = sopMapper.selectById(workOrder.getSopId());
        if (sop == null || !StringUtils.hasText(sop.getRequiredParts()) || "[]".equals(sop.getRequiredParts())) {
            return List.of();
        }
        List<String> partCodes;
        try {
            partCodes = objectMapper.readValue(sop.getRequiredParts(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
        if (partCodes == null || partCodes.isEmpty()) {
            return List.of();
        }

        List<SparePartUsageVO> deducted = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String code : partCodes) {
            if (!StringUtils.hasText(code)) continue;
            String trimmed = code.trim();
            SparePart part = partMapper.selectOne(new QueryWrapper<SparePart>().eq("part_code", trimmed));
            if (part == null) {
                missing.add("备件不存在: " + trimmed);
                continue;
            }
            if (part.getQuantity() == null || part.getQuantity() < 1) {
                missing.add("库存为 0: " + trimmed);
                continue;
            }
            Long existing = usageMapper.selectCount(new QueryWrapper<SparePartUsage>()
                    .eq("work_order_id", workOrder.getId())
                    .eq("part_id", part.getId()));
            if (existing != null && existing > 0) {
                continue;
            }
            part.setQuantity(part.getQuantity() - 1);
            part.setUpdatedAt(now);
            partMapper.updateById(part);

            SparePartUsage usage = new SparePartUsage();
            usage.setPartId(part.getId());
            usage.setWorkOrderId(workOrder.getId());
            usage.setQuantity(1);
            usage.setUserName("SOP自动");
            usage.setNote("工单闭环关联 SOP " + sop.getSopCode() + " 触发自动扣减");
            usage.setUsedAt(now);
            usage.setCreatedAt(now);
            usageMapper.insert(usage);

            deducted.add(toUsageVO(usage, part));
        }

        if (!missing.isEmpty()) {
            String oldDesc = workOrder.getDescription() == null ? "" : workOrder.getDescription();
            workOrder.setDescription(oldDesc + " | SOP备件扣减异常: " + String.join("; ", missing));
            workOrder.setUpdatedAt(now);
            workOrderMapper.updateById(workOrder);
        }
        return deducted;
    }

    private SparePart requirePart(Long id) {
        SparePart part = partMapper.selectById(id);
        if (part == null) {
            throw new IllegalArgumentException("备件不存在: " + id);
        }
        return part;
    }

    private SparePartVO toVO(SparePart entity) {
        SparePartVO vo = new SparePartVO();
        BeanUtils.copyProperties(entity, vo);
        int safety = entity.getSafetyStock() == null ? 0 : entity.getSafetyStock();
        int qty = entity.getQuantity() == null ? 0 : entity.getQuantity();
        vo.setLowStock(qty < safety);
        return vo;
    }

    private SparePartUsageVO toUsageVO(SparePartUsage usage, SparePart part) {
        SparePartUsageVO vo = new SparePartUsageVO();
        BeanUtils.copyProperties(usage, vo);
        if (part != null) {
            vo.setPartCode(part.getPartCode());
            vo.setPartName(part.getName());
        }
        if (usage.getWorkOrderId() != null) {
            WorkOrder order = workOrderMapper.selectById(usage.getWorkOrderId());
            if (order != null) {
                vo.setWorkOrderNo(order.getOrderNo());
            }
        }
        return vo;
    }
}
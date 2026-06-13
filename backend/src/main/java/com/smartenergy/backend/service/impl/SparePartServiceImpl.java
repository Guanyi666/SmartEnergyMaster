package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.SparePartCreateRequest;
import com.smartenergy.backend.dto.SparePartUsageRequest;
import com.smartenergy.backend.entity.MaintenanceSOPRequiredPart;
import com.smartenergy.backend.entity.SparePart;
import com.smartenergy.backend.entity.SparePartUsage;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.mapper.MaintenanceSOPRequiredPartMapper;
import com.smartenergy.backend.mapper.SparePartMapper;
import com.smartenergy.backend.mapper.SparePartUsageMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.service.SparePartService;
import com.smartenergy.backend.vo.SparePartUsageVO;
import com.smartenergy.backend.vo.SparePartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * v4 改造：
 * - spare_part_usage 不再写 createdAt（v3 死字段已删）
 * - SOP 备件清单改读子表 maintenance_sop_required_part
 * - I 修复：spare_part_usage 写 user_id（从 auth name 推断 sys_user.id）
 */
@Service
@RequiredArgsConstructor
public class SparePartServiceImpl implements SparePartService {

    private final SparePartMapper partMapper;
    private final SparePartUsageMapper usageMapper;
    private final WorkOrderMapper workOrderMapper;
    private final MaintenanceSOPRequiredPartMapper sopRequiredPartMapper;

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
        LocalDateTime now = LocalDateTime.now();
        if (partMapper.deductStock(part.getId(), request.getQuantity(), now) == 0) {
            SparePart current = requirePart(part.getId());
            throw new IllegalArgumentException("库存不足：当前 " + current.getQuantity() + " "
                    + current.getUnit() + "，申请 " + request.getQuantity());
        }
        part = requirePart(part.getId());

        // 写领用记录（v4: 删 setCreatedAt, 加 userId）
        SparePartUsage usage = new SparePartUsage();
        usage.setPartId(request.getPartId());
        usage.setWorkOrderId(request.getWorkOrderId());
        usage.setQuantity(request.getQuantity());
        usage.setUserName(request.getUserName());
        usage.setNote(request.getNote());
        usage.setUsedAt(now);
        usageMapper.insert(usage);

        return toUsageVO(usage, part);
    }

    @Override
    @Transactional
    public List<SparePartUsageVO> recordUsages(List<SparePartUsageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一种配件");
        }
        return requests.stream().map(this::recordUsage).toList();
    }

    @Override
    public List<SparePartUsageVO> listUsages(Long partId, String userName, int limit) {
        // ★ NC3 防御纵深: 钳制 [1, 500] 防止单次过大 LIMIT + 未来 refactor 风险
        int safeLimit = Math.min(Math.max(1, limit), 500);
        QueryWrapper<SparePartUsage> wrapper = new QueryWrapper<SparePartUsage>()
                .orderByDesc("used_at")
                .last("LIMIT " + safeLimit);
        if (partId != null) {
            wrapper.eq("part_id", partId);
        }
        if (StringUtils.hasText(userName)) {
            wrapper.eq("user_name", userName);
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
        // v4: 从 maintenance_sop_required_part 子表读所需备件
        List<MaintenanceSOPRequiredPart> requiredParts = sopRequiredPartMapper.selectList(
                new QueryWrapper<MaintenanceSOPRequiredPart>().eq("sop_id", workOrder.getSopId()));
        if (requiredParts == null || requiredParts.isEmpty()) {
            return List.of();
        }

        List<SparePartUsageVO> deducted = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (MaintenanceSOPRequiredPart req : requiredParts) {
            String code = req.getPartCode();
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
            usage.setNote("工单闭环关联 SOP " + req.getSopId() + " 触发自动扣减");
            usage.setUsedAt(now);
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

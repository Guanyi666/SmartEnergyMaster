package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.dto.MaintenancePersonnelRequest;
import com.smartenergy.backend.dto.PageQuery;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.mapper.MaintenancePersonnelMapper;
import com.smartenergy.backend.service.MaintenancePersonnelService;
import com.smartenergy.backend.vo.MaintenancePersonnelVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenancePersonnelServiceImpl implements MaintenancePersonnelService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private final MaintenancePersonnelMapper personnelMapper;

    @Override
    public Page<MaintenancePersonnelVO> list(PageQuery pageQuery, String specialization,
                                             String skillLevel, Boolean onDuty) {
        Page<MaintenancePersonnel> page = new Page<>(
                pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum(),
                pageQuery.getPageSize() == null ? 20 : pageQuery.getPageSize()
        );

        QueryWrapper<MaintenancePersonnel> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(skillLevel)) {
            wrapper.eq("skill_level", skillLevel.toUpperCase());
        }
        if (onDuty != null) {
            wrapper.eq("is_on_duty", onDuty);
        }
        // specializations 是 JSONB，应用层做 contains 过滤（人员量 < 100 性能可接受）
        wrapper.orderByAsc("employee_no");

        Page<MaintenancePersonnel> result = personnelMapper.selectPage(page, wrapper);

        Page<MaintenancePersonnelVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<MaintenancePersonnelVO> records = result.getRecords().stream()
                .map(this::toVO)
                .filter(vo -> {
                    if (!StringUtils.hasText(specialization)) return true;
                    if (vo.getSpecializations() == null) return false;
                    return vo.getSpecializations().stream()
                            .anyMatch(s -> s.contains(specialization));
                })
                .toList();
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public MaintenancePersonnelVO getById(Long id) {
        MaintenancePersonnel entity = personnelMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("人员不存在: id=" + id);
        }
        return toVO(entity);
    }

    @Override
    @Transactional
    public MaintenancePersonnelVO create(MaintenancePersonnelRequest req) {
        // 唯一性校验
        Long exist = personnelMapper.selectCount(new QueryWrapper<MaintenancePersonnel>()
                .eq("employee_no", req.getEmployeeNo()));
        if (exist != null && exist > 0) {
            throw new IllegalArgumentException("工号已存在: " + req.getEmployeeNo());
        }
        MaintenancePersonnel entity = new MaintenancePersonnel();
        BeanUtils.copyProperties(req, entity);
        if (!StringUtils.hasText(entity.getAvatarColor())) {
            entity.setAvatarColor("#52c8ff");
        }
        entity.setSpecializations(serializeList(req.getSpecializations()));
        entity.setCurrentWorkload(0);
        if (entity.getIsOnDuty() == null) {
            entity.setIsOnDuty(true);
        }
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        personnelMapper.insert(entity);
        log.info("[Personnel] 创建: employeeNo={}, name={}", entity.getEmployeeNo(), entity.getName());
        return toVO(entity);
    }

    @Override
    @Transactional
    public MaintenancePersonnelVO update(Long id, MaintenancePersonnelRequest req) {
        MaintenancePersonnel entity = personnelMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("人员不存在: id=" + id);
        }
        // employeeNo 不允许修改
        entity.setName(req.getName());
        entity.setPhone(req.getPhone());
        entity.setEmail(req.getEmail());
        if (StringUtils.hasText(req.getAvatarColor())) {
            entity.setAvatarColor(req.getAvatarColor());
        }
        if (req.getSpecializations() != null) {
            entity.setSpecializations(serializeList(req.getSpecializations()));
        }
        entity.setSkillLevel(req.getSkillLevel());
        entity.setCertification(req.getCertification());
        entity.setMaxWorkload(req.getMaxWorkload());
        entity.setUpdatedAt(LocalDateTime.now());
        personnelMapper.updateById(entity);
        log.info("[Personnel] 更新: id={}, name={}", id, entity.getName());
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        MaintenancePersonnel entity = personnelMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("人员不存在: id=" + id);
        }
        if (entity.getCurrentWorkload() != null && entity.getCurrentWorkload() > 0) {
            throw new IllegalStateException(
                "该人员当前有 " + entity.getCurrentWorkload() + " 单在处理，无法删除（请先转派或闭环）"
            );
        }
        personnelMapper.deleteById(id);
        log.info("[Personnel] 删除: id={}, employeeNo={}", entity.getEmployeeNo());
    }

    @Override
    @Transactional
    public MaintenancePersonnelVO toggleDuty(Long id, boolean onDuty) {
        MaintenancePersonnel entity = personnelMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("人员不存在: id=" + id);
        }
        entity.setIsOnDuty(onDuty);
        entity.setUpdatedAt(LocalDateTime.now());
        personnelMapper.updateById(entity);
        log.info("[Personnel] 切岗: id={}, onDuty={}", id, onDuty);
        return toVO(entity);
    }

    @Override
    public MaintenancePersonnelVO toVO(MaintenancePersonnel entity) {
        if (entity == null) return null;
        MaintenancePersonnelVO vo = new MaintenancePersonnelVO();
        BeanUtils.copyProperties(entity, vo);
        // specializations 是 JSON 字符串，转换为 List<String> 供前端
        vo.setSpecializations(parseList(entity.getSpecializations()));
        // 计算负载率
        if (entity.getMaxWorkload() != null && entity.getMaxWorkload() > 0
                && entity.getCurrentWorkload() != null) {
            int rate = (int) Math.round(
                entity.getCurrentWorkload() * 100.0 / entity.getMaxWorkload()
            );
            vo.setWorkloadRate(rate);
        } else {
            vo.setWorkloadRate(0);
        }
        return vo;
    }

    // ===== JSON 辅助方法 =====
    private static String serializeList(List<String> list) {
        if (list == null) return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalArgumentException("技能列表 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private static List<String> parseList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            log.warn("[Personnel] specializations 解析失败: json={}", json, e);
            return Collections.emptyList();
        }
    }
}

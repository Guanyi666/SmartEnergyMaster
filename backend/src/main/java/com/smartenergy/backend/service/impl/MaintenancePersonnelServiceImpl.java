package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.dto.MaintenancePersonnelRequest;
import com.smartenergy.backend.dto.PageQuery;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.entity.MaintenancePersonnelArchive;
import com.smartenergy.backend.mapper.MaintenancePersonnelArchiveMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelMapper;
import com.smartenergy.backend.service.MaintenancePersonnelService;
import com.smartenergy.backend.vo.MaintenancePersonnelFullVO;
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

/**
 * v4 改造：双 Mapper 模式（personnelMapper + personnelArchiveMapper）。
 * - 排班字段（avatarColor/currentWorkload/maxWorkload/isOnDuty）走 workorder_maintenance_personnel
 * - 员工档案字段（name/phone/email/specializations/skillLevel/certification）走 maintenance_personnel
 * - 两者通过 user_id 关联 sys_user 形成完整视图
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenancePersonnelServiceImpl implements MaintenancePersonnelService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private final MaintenancePersonnelMapper personnelMapper;
    private final MaintenancePersonnelArchiveMapper archiveMapper;

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
        LocalDateTime now = LocalDateTime.now();

        // v4: 写排班表（workorder_maintenance_personnel）
        MaintenancePersonnel entity = new MaintenancePersonnel();
        entity.setEmployeeNo(req.getEmployeeNo());
        entity.setUserId(req.getUserId());
        if (!StringUtils.hasText(req.getAvatarColor())) {
            entity.setAvatarColor("#52c8ff");
        } else {
            entity.setAvatarColor(req.getAvatarColor());
        }
        entity.setCurrentWorkload(0);
        entity.setMaxWorkload(req.getMaxWorkload());
        if (entity.getIsOnDuty() == null) {
            entity.setIsOnDuty(true);
        }
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        personnelMapper.insert(entity);

        // v4: 同步写档案表（maintenance_personnel）
        MaintenancePersonnelArchive archive = upsertArchiveByEmployeeNo(req.getEmployeeNo(),
                req.getName(), req.getPhone(), req.getEmail(),
                serializeList(req.getSpecializations()),
                req.getSkillLevel(), req.getCertification(),
                req.getUserId(), now);

        log.info("[Personnel] 创建: employeeNo={}, name={}", entity.getEmployeeNo(), archive.getName());
        return toVO(entity);
    }

    @Override
    @Transactional
    public MaintenancePersonnelVO update(Long id, MaintenancePersonnelRequest req) {
        MaintenancePersonnel entity = personnelMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("人员不存在: id=" + id);
        }
        LocalDateTime now = LocalDateTime.now();

        // employeeNo 不允许修改
        if (StringUtils.hasText(req.getAvatarColor())) {
            entity.setAvatarColor(req.getAvatarColor());
        }
        if (req.getMaxWorkload() != null) {
            entity.setMaxWorkload(req.getMaxWorkload());
        }
        if (req.getUserId() != null) {
            entity.setUserId(req.getUserId());
        }
        entity.setUpdatedAt(now);
        personnelMapper.updateById(entity);

        // 同步写档案表
        MaintenancePersonnelArchive archive = upsertArchiveByEmployeeNo(entity.getEmployeeNo(),
                req.getName(), req.getPhone(), req.getEmail(),
                serializeList(req.getSpecializations()),
                req.getSkillLevel(), req.getCertification(),
                req.getUserId(), now);

        log.info("[Personnel] 更新: id={}, name={}", id, archive.getName());
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
        // 注意：v4 不级联删除 archive（按 user_id 关联的档案独立维护）
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
        // v4: 查 archive 补全员工档案字段
        MaintenancePersonnelArchive archive = archiveMapper.selectOne(new QueryWrapper<MaintenancePersonnelArchive>()
                .eq("employee_no", entity.getEmployeeNo()));
        if (archive != null) {
            vo.setName(archive.getName());
            vo.setPhone(archive.getPhone());
            vo.setEmail(archive.getEmail());
            vo.setSpecializations(parseList(archive.getSpecializations()));
            vo.setSkillLevel(archive.getSkillLevel());
            vo.setCertification(archive.getCertification());
            vo.setUserId(archive.getUserId());
        }
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

    /** 同步 upsert archive 表 */
    private MaintenancePersonnelArchive upsertArchiveByEmployeeNo(String employeeNo, String name,
                                                                  String phone, String email,
                                                                  String specializations,
                                                                  String skillLevel, String certification,
                                                                  Integer userId, LocalDateTime now) {
        if (!StringUtils.hasText(employeeNo)) return null;
        MaintenancePersonnelArchive archive = archiveMapper.selectOne(new QueryWrapper<MaintenancePersonnelArchive>()
                .eq("employee_no", employeeNo));
        if (archive == null) {
            archive = new MaintenancePersonnelArchive();
            archive.setEmployeeNo(employeeNo);
            archive.setCreatedAt(now);
        }
        if (StringUtils.hasText(name)) archive.setName(name);
        if (phone != null) archive.setPhone(phone);
        if (email != null) archive.setEmail(email);
        if (specializations != null) archive.setSpecializations(specializations);
        if (StringUtils.hasText(skillLevel)) archive.setSkillLevel(skillLevel);
        if (certification != null) archive.setCertification(certification);
        if (userId != null) archive.setUserId(userId);
        archive.setUpdatedAt(now);
        if (archive.getId() == null) {
            archiveMapper.insert(archive);
        } else {
            archiveMapper.updateById(archive);
        }
        return archive;
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

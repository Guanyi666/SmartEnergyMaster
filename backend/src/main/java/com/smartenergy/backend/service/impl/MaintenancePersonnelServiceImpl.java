package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.dto.MaintenancePersonnelRequest;
import com.smartenergy.backend.dto.PageQuery;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.entity.MaintenancePersonnelArchive;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.MaintenancePersonnelArchiveMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelMapper;
import com.smartenergy.backend.mapper.SysUserMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenancePersonnelServiceImpl implements MaintenancePersonnelService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private final MaintenancePersonnelMapper personnelMapper;
    private final MaintenancePersonnelArchiveMapper archiveMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public Page<MaintenancePersonnelVO> list(PageQuery pageQuery, String specialization,
                                             String skillLevel, Boolean onDuty) {
        Page<MaintenancePersonnel> page = new Page<>(
                pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum(),
                pageQuery.getPageSize() == null ? 20 : pageQuery.getPageSize()
        );

        QueryWrapper<MaintenancePersonnel> wrapper = new QueryWrapper<>();
        wrapper.inSql("user_id", "SELECT id FROM sys_user WHERE role = 'MAINTENANCE_ENGINEER'");
        if (onDuty != null) {
            wrapper.eq("is_on_duty", onDuty);
        }
        wrapper.orderByDesc("created_at");

        Page<MaintenancePersonnel> result = personnelMapper.selectPage(page, wrapper);

        Page<MaintenancePersonnelVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<MaintenancePersonnelVO> records = result.getRecords().stream()
                .map(this::toVO)
                .filter(vo -> {
                    if (!StringUtils.hasText(skillLevel) && !StringUtils.hasText(specialization)) return true;
                    if (StringUtils.hasText(skillLevel) && !skillLevel.equalsIgnoreCase(vo.getSkillLevel())) return false;
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
    @Transactional(rollbackFor = Exception.class)
    public MaintenancePersonnelVO create(MaintenancePersonnelRequest req) {
        LocalDateTime now = LocalDateTime.now();

        Integer userId = req.getUserId();
        if (userId != null) {
            SysUser existing = sysUserMapper.selectById(userId);
            if (existing == null) {
                throw new IllegalArgumentException("指定的账号不存在: userId=" + userId);
            }
            if (personnelMapper.selectCount(new QueryWrapper<MaintenancePersonnel>().eq("user_id", userId)) > 0) {
                throw new IllegalArgumentException("该账号已有排班记录");
            }
        }
        // userId 必须提供（账号已通过人员管理页面创建）
        if (userId == null) {
            throw new IllegalArgumentException("必须指定关联的账号 userId");
        }

        MaintenancePersonnel entity = new MaintenancePersonnel();
        entity.setUserId(userId);
        entity.setAvatarColor(StringUtils.hasText(req.getAvatarColor()) ? req.getAvatarColor() : "#52c8ff");
        entity.setCurrentWorkload(0);
        entity.setMaxWorkload(req.getMaxWorkload());
        entity.setIsOnDuty(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        personnelMapper.insert(entity);

        MaintenancePersonnelArchive archive = upsertArchive(userId,
                req.getName(), req.getPhone(), req.getEmail(),
                serializeList(req.getSpecializations()),
                req.getSkillLevel(), req.getCertification(), now);

        log.info("[Personnel] 创建完成: userId={}, name={}", userId, archive.getName());
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

        if (StringUtils.hasText(req.getAvatarColor())) {
            entity.setAvatarColor(req.getAvatarColor());
        }
        if (req.getMaxWorkload() != null) {
            entity.setMaxWorkload(req.getMaxWorkload());
        }
        entity.setUpdatedAt(now);
        personnelMapper.updateById(entity);

        MaintenancePersonnelArchive archive = upsertArchive(entity.getUserId(),
                req.getName(), req.getPhone(), req.getEmail(),
                serializeList(req.getSpecializations()),
                req.getSkillLevel(), req.getCertification(), now);

        log.info("[Personnel] 更新: id={}, name={}", id, archive.getName());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        Integer userId = entity.getUserId();
        if (userId != null) {
            archiveMapper.delete(new QueryWrapper<MaintenancePersonnelArchive>().eq("user_id", userId));
        }

        personnelMapper.deleteById(id);
        log.info("[Personnel] 删除完成: id={}, userId={}", id, userId);
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
        if (entity.getUserId() != null) {
            MaintenancePersonnelArchive archive = archiveMapper.selectOne(
                    new QueryWrapper<MaintenancePersonnelArchive>().eq("user_id", entity.getUserId()));
            if (archive != null) {
                vo.setName(archive.getName());
                vo.setPhone(archive.getPhone());
                vo.setEmail(archive.getEmail());
                vo.setSpecializations(parseList(archive.getSpecializations()));
                vo.setSkillLevel(archive.getSkillLevel());
                vo.setCertification(archive.getCertification());
                vo.setUserId(archive.getUserId());
            }
        }
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

    private MaintenancePersonnelArchive upsertArchive(Integer userId, String name,
                                                      String phone, String email,
                                                      String specializations,
                                                      String skillLevel, String certification,
                                                      LocalDateTime now) {
        if (userId == null) return null;
        MaintenancePersonnelArchive archive = archiveMapper.selectOne(
                new QueryWrapper<MaintenancePersonnelArchive>().eq("user_id", userId));
        if (archive == null) {
            archive = new MaintenancePersonnelArchive();
            archive.setUserId(userId);
            archive.setCreatedAt(now);
        }
        if (StringUtils.hasText(name)) archive.setName(name);
        if (phone != null) archive.setPhone(phone);
        if (email != null) archive.setEmail(email);
        if (specializations != null) archive.setSpecializations(specializations);
        if (StringUtils.hasText(skillLevel)) archive.setSkillLevel(skillLevel);
        if (certification != null) archive.setCertification(certification);
        archive.setUpdatedAt(now);
        if (archive.getId() == null) {
            archiveMapper.insert(archive);
        } else {
            archiveMapper.updateById(archive);
        }
        return archive;
    }

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

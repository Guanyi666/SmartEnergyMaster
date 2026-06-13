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
import com.smartenergy.backend.service.UserService;
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
    // ★ 批次 2 注入: 用于 create() 自动建账号 (C3) + delete() 联动禁账号 (C4)
    private final SysUserMapper sysUserMapper;
    private final UserService userService;

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
    @Transactional(rollbackFor = Exception.class)
    public MaintenancePersonnelVO create(MaintenancePersonnelRequest req) {
        // 唯一性校验
        Long exist = personnelMapper.selectCount(new QueryWrapper<MaintenancePersonnel>()
                .eq("employee_no", req.getEmployeeNo()));
        if (exist != null && exist > 0) {
            throw new IllegalArgumentException("工号已存在: " + req.getEmployeeNo());
        }
        LocalDateTime now = LocalDateTime.now();

        // ★ 批次 2 C3 + H1 修复 (2026-06-13): 账号-员工 1:1 完整性
        //   ① 若 userId 已传 → 校验 sys_user 存在性 (堵无效 FK)
        //   ② 若 userId 未传 → 自动调 userService.createMaintenanceAccount 建账号
        //      → 用 employee_no 当 username (必须符合 2026030xxx 格式),初始密码 username@Init
        //   修复前: userId 任意传值无校验,且不传时永远建不出可登录账号 (幽灵员工)
        Integer userId = req.getUserId();
        if (userId != null) {
            SysUser existing = sysUserMapper.selectById(userId);
            if (existing == null) {
                throw new IllegalArgumentException("指定的账号不存在: userId=" + userId
                        + " (H1: 必须先在系统配置中创建账号,或留空让系统自动创建)");
            }
        } else {
            userId = userService.createMaintenanceAccount(
                    req.getEmployeeNo(), req.getName(), req.getPhone(), req.getEmail());
            log.info("[Personnel] C3 自动建账号 → userId={}, employeeNo={}",
                    userId, req.getEmployeeNo());
        }

        // v4: 写排班表（workorder_maintenance_personnel）
        MaintenancePersonnel entity = new MaintenancePersonnel();
        entity.setEmployeeNo(req.getEmployeeNo());
        entity.setUserId(userId);
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
                userId, now);

        log.info("[Personnel] 创建完成: employeeNo={}, name={}, userId={}",
                entity.getEmployeeNo(), archive.getName(), userId);
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

        // ★ 批次 2 C4 + M2 合并修复 (2026-06-13): 防幽灵账号 + 防孤儿 assignment
        //   旧版只 personnelMapper.deleteById(id),sys_user 账号继续可登录 = 幽灵账号
        //   修复后 3 步:
        //     1) 先禁用 sys_user 账号 (不物理删,保留审计关联)
        //     2) 清理 archive 档案 (按 employee_no 关联)
        //     3) 最后删 workorder_maintenance_personnel 排班
        Integer userId = entity.getUserId();
        if (userId != null) {
            try {
                userService.updateStatus(userId, "DISABLED");
                log.info("[Personnel] C4 联动禁用账号: userId={}", userId);
            } catch (Exception e) {
                // 内置管理员或其它特殊账号无法禁用 → 阻止人员删除以保证一致性
                throw new IllegalStateException("无法禁用关联账号 (userId=" + userId
                        + "): " + e.getMessage() + " — 请先在系统配置中处理该账号", e);
            }
        }
        // 清理档案表 (按 employee_no, 因为 archive 与 schedule 通过 employee_no 弱关联)
        int archiveDeleted = archiveMapper.delete(new QueryWrapper<MaintenancePersonnelArchive>()
                .eq("employee_no", entity.getEmployeeNo()));
        log.info("[Personnel] 清理 archive: employeeNo={}, deleted={}",
                entity.getEmployeeNo(), archiveDeleted);

        personnelMapper.deleteById(id);
        log.info("[Personnel] 删除完成: id={}, employeeNo={}, userId={}",
                id, entity.getEmployeeNo(), userId);
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

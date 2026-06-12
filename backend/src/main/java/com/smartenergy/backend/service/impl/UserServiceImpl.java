package com.smartenergy.backend.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.config.JwtConfig;
import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.mapper.UserWithPersonnelMapper;
import com.smartenergy.backend.service.AuditLogService;
import com.smartenergy.backend.service.LoginSessionService;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.LoginVO;
import com.smartenergy.backend.vo.PageVO;
import com.smartenergy.backend.vo.UserVO;
import com.smartenergy.backend.vo.UserWithPersonnelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String BUILT_IN_ADMIN_USERNAME = "admin";
    private static final String ADMIN_ROLE = "ADMIN";

    private final SysUserMapper sysUserMapper;
    private final UserWithPersonnelMapper userWithPersonnelMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtConfig jwtConfig;
    private final AuditLogService auditLogService;
    private final LoginSessionService loginSessionService;

    @Override
    public LoginVO login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String role = authentication.getAuthorities().iterator().next().getAuthority();
        SysUser user = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", request.getUsername()));
        if (user != null) {
            user.setLastLoginAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            sysUserMapper.updateById(user);
        }
        String token = JWT.create()
                .setPayload("username", request.getUsername())
                .setPayload("role", role)
                .setPayload("expire_time", System.currentTimeMillis() + jwtConfig.getExpirationMs())
                .setKey(jwtConfig.getKeyBytes())
                .sign();
        if (!loginSessionService.claim(request.getUsername(), token)) {
            throw new IllegalStateException("该账号当前已在线，不能重复登录");
        }

        return LoginVO.builder()
                .token(token)
                .username(request.getUsername())
                .role(role)
                .nickname(user == null ? null : user.getNickname())
                .department(user == null ? null : user.getDepartment())
                .build();
    }

    @Override
    public void logout(String username, String token) {
        if (StringUtils.hasText(username) && StringUtils.hasText(token)) {
            loginSessionService.release(username, token);
        }
    }

    @Override
    public PageVO<UserVO> listUsers(int page, int size, String keyword, String role, String department, String status) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<SysUser>().orderByDesc("created_at");
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("username", keyword).or().like("nickname", keyword)
                    .or().like("phone", keyword).or().like("email", keyword));
        }
        if (StringUtils.hasText(role)) wrapper.eq("role", role);
        if (StringUtils.hasText(department)) wrapper.eq("department", department);
        if (StringUtils.hasText(status)) wrapper.eq("status", status);
        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)), wrapper);
        PageVO<UserVO> response = new PageVO<>();
        response.setPage((int) result.getCurrent());
        response.setSize((int) result.getSize());
        response.setTotal(result.getTotal());
        response.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return response;
    }

    @Override
    @Transactional
    public UserVO createUser(UserUpsertRequest request) {
        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("新建用户必须设置初始密码");
        }
        if (BUILT_IN_ADMIN_USERNAME.equalsIgnoreCase(request.getUsername().trim())) {
            throw new IllegalArgumentException("用户名 admin 为内置管理员保留");
        }
        requireAdminForAdminRoleChange(null, request.getRole());
        if (sysUserMapper.exists(new QueryWrapper<SysUser>().eq("username", request.getUsername()))) {
            throw new IllegalArgumentException("用户名已存在");
        }
        SysUser user = new SysUser();
        copyEditableFields(request, user);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        auditLogService.record("CREATE", "USER", "SYS_USER", String.valueOf(user.getId()),
                java.util.Map.of("username", user.getUsername(), "role", user.getRole()));
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO updateUser(Integer id, UserUpsertRequest request) {
        SysUser user = requireUser(id);
        protectBuiltInAdmin(user, request);
        requireAdminForAdminRoleChange(user.getRole(), request.getRole());
        if (!user.getUsername().equals(request.getUsername())
                && sysUserMapper.exists(new QueryWrapper<SysUser>().eq("username", request.getUsername()).ne("id", id))) {
            throw new IllegalArgumentException("用户名已存在");
        }
        copyEditableFields(request, user);
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        auditLogService.record("UPDATE", "USER", "SYS_USER", String.valueOf(user.getId()),
                java.util.Map.of("username", user.getUsername(), "role", user.getRole()));
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO updateStatus(Integer id, String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("状态仅支持 ACTIVE 或 DISABLED");
        }
        SysUser user = requireUser(id);
        if (isBuiltInAdmin(user) && !"ACTIVE".equals(status)) {
            throw new IllegalArgumentException("内置管理员 admin 必须保持启用状态");
        }
        requireAdminForAdminRoleChange(user.getRole(), user.getRole());
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        auditLogService.record("STATUS_CHANGE", "USER", "SYS_USER", String.valueOf(user.getId()),
                java.util.Map.of("username", user.getUsername(), "status", status));
        return toVO(user);
    }

    @Override
    @Transactional
    public void deleteUser(Integer id) {
        SysUser user = requireUser(id);
        if (isBuiltInAdmin(user)) {
            throw new IllegalArgumentException("不能删除内置管理员账号");
        }
        if (user.getUsername().equalsIgnoreCase(currentUsername())) {
            throw new IllegalArgumentException("不能删除当前登录账号");
        }
        requireAdminForAdminRoleChange(user.getRole(), null);
        sysUserMapper.deleteById(id);
        auditLogService.record("DELETE", "USER", "SYS_USER", String.valueOf(id),
                java.util.Map.of("username", user.getUsername()));
    }

    private SysUser requireUser(Integer id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return user;
    }

    private void protectBuiltInAdmin(SysUser user, UserUpsertRequest request) {
        if (!isBuiltInAdmin(user)) {
            if (BUILT_IN_ADMIN_USERNAME.equalsIgnoreCase(request.getUsername().trim())) {
                throw new IllegalArgumentException("用户名 admin 为内置管理员保留");
            }
            return;
        }
        if (!BUILT_IN_ADMIN_USERNAME.equalsIgnoreCase(request.getUsername().trim())) {
            throw new IllegalArgumentException("内置管理员 admin 不能修改用户名");
        }
        if (!ADMIN_ROLE.equals(request.getRole())) {
            throw new IllegalArgumentException("内置管理员 admin 永远只能是系统管理员");
        }
    }

    private void requireAdminForAdminRoleChange(String oldRole, String newRole) {
        if ((ADMIN_ROLE.equals(oldRole) || ADMIN_ROLE.equals(newRole)) && !currentUserIsAdmin()) {
            throw new AccessDeniedException("只有系统管理员可以授予或修改系统管理员身份");
        }
    }

    private boolean isBuiltInAdmin(SysUser user) {
        return BUILT_IN_ADMIN_USERNAME.equalsIgnoreCase(user.getUsername());
    }

    private boolean currentUserIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_ROLE.equals(authority.getAuthority()));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "" : authentication.getName();
    }

    private void copyEditableFields(UserUpsertRequest request, SysUser user) {
        user.setUsername(request.getUsername().trim());
        user.setRole(request.getRole());
        user.setNickname(request.getNickname());
        user.setDepartment(request.getDepartment());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * v6: 人员管理合并列表
     * 流程：1) 查 sys_user（分页+筛选）→ 2) 批量查 maintenance_personnel + workorder_maintenance_personnel → 3) 组装 VO
     */
    @Override
    public PageVO<UserWithPersonnelVO> listUsersWithPersonnel(int page, int size, String keyword,
                                                              String role, String department, String status,
                                                              Boolean isMaintenance) {
        Page<SysUser> pageReq = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("username", keyword).or().like("nickname", keyword)
                    .or().like("phone", keyword).or().like("email", keyword));
        }
        if (StringUtils.hasText(role)) wrapper.eq("role", role);
        if (StringUtils.hasText(department)) wrapper.eq("department", department);
        if (StringUtils.hasText(status)) wrapper.eq("status", status);
        wrapper.orderByDesc("created_at");

        Page<SysUser> result = sysUserMapper.selectPage(pageReq, wrapper);
        List<SysUser> users = result.getRecords();
        if (users.isEmpty()) {
            PageVO<UserWithPersonnelVO> vo = new PageVO<>();
            vo.setPage((int) result.getCurrent());
            vo.setSize((int) result.getSize());
            vo.setTotal(result.getTotal());
            vo.setRecords(List.of());
            return vo;
        }

        // 批量查档案和排班
        List<Integer> userIds = users.stream().map(SysUser::getId).toList();
        Map<Integer, Map<String, Object>> archiveMap = userWithPersonnelMapper
                .selectArchivesByUserIds(userIds).stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("user_id")).intValue(),
                        m -> m,
                        (a, b) -> a));
        Map<Integer, Map<String, Object>> scheduleMap = userWithPersonnelMapper
                .selectSchedulesByUserIds(userIds).stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("user_id")).intValue(),
                        m -> m,
                        (a, b) -> a));

        // 组装 VO
        List<UserWithPersonnelVO> records = users.stream().map(user -> {
            UserWithPersonnelVO vo = new UserWithPersonnelVO();
            // sys_user 字段
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setRole(user.getRole());
            vo.setDepartment(user.getDepartment());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setStatus(user.getStatus());
            vo.setLastLoginAt(user.getLastLoginAt());
            vo.setCreatedAt(user.getCreatedAt());

            // 档案
            Map<String, Object> archive = archiveMap.get(user.getId());
            if (archive != null) {
                vo.setEmployeeNo((String) archive.get("employee_no"));
                vo.setArchiveName((String) archive.get("name"));
                vo.setArchivePhone((String) archive.get("phone"));
                vo.setArchiveEmail((String) archive.get("email"));
                vo.setSpecializations((String) archive.get("specializations"));
                vo.setSkillLevel((String) archive.get("skill_level"));
                vo.setCertification((String) archive.get("certification"));
            }

            // 排班
            Map<String, Object> schedule = scheduleMap.get(user.getId());
            if (schedule != null) {
                vo.setAvatarColor((String) schedule.get("avatar_color"));
                Object cw = schedule.get("current_workload");
                Object mw = schedule.get("max_workload");
                vo.setCurrentWorkload(cw == null ? 0 : ((Number) cw).intValue());
                vo.setMaxWorkload(mw == null ? 0 : ((Number) mw).intValue());
                Object duty = schedule.get("is_on_duty");
                vo.setIsOnDuty(duty == null ? Boolean.FALSE : (Boolean) duty);
                // 计算负载率
                if (vo.getMaxWorkload() != null && vo.getMaxWorkload() > 0
                        && vo.getCurrentWorkload() != null) {
                    int rate = (int) Math.round(vo.getCurrentWorkload() * 100.0 / vo.getMaxWorkload());
                    vo.setWorkloadRate(rate);
                } else {
                    vo.setWorkloadRate(0);
                }
            }

            // 标志：是否是维修人员
            boolean isMaint = "MAINTENANCE_ENGINEER".equals(user.getRole())
                    || archive != null
                    || schedule != null;
            vo.setIsMaintenance(isMaint);

            return vo;
        }).filter(vo -> isMaintenance == null || isMaintenance.equals(vo.getIsMaintenance()))
                .collect(Collectors.toList());

        PageVO<UserWithPersonnelVO> vo = new PageVO<>();
        vo.setPage((int) result.getCurrent());
        vo.setSize((int) result.getSize());
        vo.setTotal(result.getTotal());
        vo.setRecords(records);
        return vo;
    }
}

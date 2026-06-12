package com.smartenergy.backend.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.config.JwtConfig;
import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.AccountSettingsRequest;
import com.smartenergy.backend.dto.MaintenanceProfileRequest;
import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.entity.MaintenancePersonnelArchive;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.entity.WorkOrderTransferRequest;
import com.smartenergy.backend.mapper.MaintenancePersonnelArchiveMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelMapper;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.mapper.UserWithPersonnelMapper;
import com.smartenergy.backend.mapper.WorkOrderTransferRequestMapper;
import com.smartenergy.backend.service.AuditLogService;
import com.smartenergy.backend.service.LoginSessionService;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.utils.AccountUsernameRules;
import com.smartenergy.backend.vo.LoginVO;
import com.smartenergy.backend.vo.AccountSettingsVO;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String BUILT_IN_ADMIN_USERNAME = AccountUsernameRules.BUILT_IN_ADMIN_USERNAME;
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String MAINTENANCE_ROLE = "MAINTENANCE_ENGINEER";

    private final SysUserMapper sysUserMapper;
    private final UserWithPersonnelMapper userWithPersonnelMapper;
    private final MaintenancePersonnelMapper maintenancePersonnelMapper;
    private final MaintenancePersonnelArchiveMapper maintenancePersonnelArchiveMapper;
    private final WorkOrderTransferRequestMapper workOrderTransferRequestMapper;
    private final ObjectMapper objectMapper;
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
    public AccountSettingsVO getAccountSettings(String username) {
        return toAccountSettingsVO(requireUser(username), false);
    }

    @Override
    @Transactional
    public AccountSettingsVO updateAccountSettings(String username, AccountSettingsRequest request) {
        SysUser user = requireUser(username);
        boolean passwordChanged = StringUtils.hasText(request.getNewPassword());
        if (passwordChanged) {
            if (!StringUtils.hasText(request.getCurrentPassword())) {
                throw new IllegalArgumentException("修改密码时必须输入当前密码");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("当前密码不正确");
            }
            if (request.getNewPassword().length() < 6) {
                throw new IllegalArgumentException("新密码不能少于 6 个字符");
            }
            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new IllegalArgumentException("新密码不能与当前密码相同");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }
        user.setPhone(normalizeOptional(request.getPhone()));
        user.setEmail(normalizeOptional(request.getEmail()));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        auditLogService.record("UPDATE", "ACCOUNT_SETTINGS", "SYS_USER", String.valueOf(user.getId()),
                Map.of("username", user.getUsername(), "passwordChanged", passwordChanged));
        return toAccountSettingsVO(user, passwordChanged);
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
        AccountUsernameRules.validate(request.getUsername(), request.getRole());
        if (BUILT_IN_ADMIN_USERNAME.equals(request.getUsername().trim())) {
            throw new IllegalArgumentException("账号 " + BUILT_IN_ADMIN_USERNAME + " 为内置管理员保留");
        }
        requireAdminForAdminRoleChange(null, request.getRole());
        if (sysUserMapper.exists(new QueryWrapper<SysUser>().eq("username", request.getUsername().trim()))) {
            throw new IllegalArgumentException("用户名已存在");
        }
        SysUser user = new SysUser();
        copyEditableFields(request, user);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        syncMaintenanceProfile(user, null, null, request);
        auditLogService.record("CREATE", "USER", "SYS_USER", String.valueOf(user.getId()),
                java.util.Map.of("username", user.getUsername(), "role", user.getRole()));
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO updateUser(Integer id, UserUpsertRequest request) {
        SysUser user = requireUser(id);
        String oldUsername = user.getUsername();
        String oldRole = user.getRole();
        protectBuiltInAdmin(user, request);
        AccountUsernameRules.validate(request.getUsername(), request.getRole());
        requireAdminForAdminRoleChange(user.getRole(), request.getRole());
        boolean usernameChanged = !user.getUsername().equals(request.getUsername().trim());
        if (usernameChanged && !currentUserIsAdmin()) {
            throw new AccessDeniedException("只有系统管理员可以修改用户账号");
        }
        if (usernameChanged
                && sysUserMapper.exists(new QueryWrapper<SysUser>().eq("username", request.getUsername().trim()).ne("id", id))) {
            throw new IllegalArgumentException("用户名已存在");
        }
        copyEditableFields(request, user);
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        syncMaintenanceProfile(user, oldUsername, oldRole, request);
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
            throw new IllegalArgumentException("内置管理员 " + BUILT_IN_ADMIN_USERNAME + " 必须保持启用状态");
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
        removeMaintenanceProfile(findSchedule(user.getId(), user.getUsername()),
                findArchive(user.getId(), user.getUsername()), user.getRole());
        sysUserMapper.deleteById(id);
        auditLogService.record("DELETE", "USER", "SYS_USER", String.valueOf(id),
                java.util.Map.of("username", user.getUsername()));
    }

    private SysUser requireUser(Integer id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return user;
    }

    private SysUser requireUser(String username) {
        SysUser user = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return user;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AccountSettingsVO toAccountSettingsVO(SysUser user, boolean passwordChanged) {
        return AccountSettingsVO.builder()
                .username(user.getUsername())
                .phone(user.getPhone())
                .email(user.getEmail())
                .passwordChanged(passwordChanged)
                .build();
    }

    private void protectBuiltInAdmin(SysUser user, UserUpsertRequest request) {
        if (!isBuiltInAdmin(user)) {
            if (BUILT_IN_ADMIN_USERNAME.equals(request.getUsername().trim())) {
                throw new IllegalArgumentException("账号 " + BUILT_IN_ADMIN_USERNAME + " 为内置管理员保留");
            }
            return;
        }
        if (!BUILT_IN_ADMIN_USERNAME.equals(request.getUsername().trim())) {
            throw new IllegalArgumentException("内置管理员账号 " + BUILT_IN_ADMIN_USERNAME + " 不能修改");
        }
        if (!ADMIN_ROLE.equals(request.getRole())) {
            throw new IllegalArgumentException("内置管理员 " + BUILT_IN_ADMIN_USERNAME + " 永远只能是系统管理员");
        }
    }

    private void requireAdminForAdminRoleChange(String oldRole, String newRole) {
        if ((ADMIN_ROLE.equals(oldRole) || ADMIN_ROLE.equals(newRole)) && !currentUserIsAdmin()) {
            throw new AccessDeniedException("只有系统管理员可以授予或修改系统管理员身份");
        }
    }

    private boolean isBuiltInAdmin(SysUser user) {
        return BUILT_IN_ADMIN_USERNAME.equals(user.getUsername());
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

    private void syncMaintenanceProfile(SysUser user, String oldUsername, String oldRole, UserUpsertRequest request) {
        String username = user.getUsername();
        String lookupUsername = StringUtils.hasText(oldUsername) ? oldUsername : username;
        MaintenancePersonnel schedule = findSchedule(user.getId(), lookupUsername);
        MaintenancePersonnelArchive archive = findArchive(user.getId(), lookupUsername);

        if (!MAINTENANCE_ROLE.equals(user.getRole())) {
            removeMaintenanceProfile(schedule, archive, oldRole);
            return;
        }

        MaintenanceProfileRequest profile = request.getMaintenanceProfile();
        if (profile == null) {
            throw new IllegalArgumentException("维修工程师必须填写维修人员档案与排班信息");
        }
        LocalDateTime now = LocalDateTime.now();

        if (schedule == null) {
            schedule = new MaintenancePersonnel();
            schedule.setCurrentWorkload(0);
            schedule.setIsOnDuty(true);
            schedule.setAvatarColor("#52c8ff");
            schedule.setCreatedAt(now);
        }
        schedule.setUserId(user.getId());
        schedule.setEmployeeNo(username);
        schedule.setMaxWorkload(profile.getMaxWorkload());
        schedule.setUpdatedAt(now);
        if (schedule.getId() == null) {
            maintenancePersonnelMapper.insert(schedule);
        } else {
            maintenancePersonnelMapper.updateById(schedule);
        }

        if (archive == null) {
            archive = new MaintenancePersonnelArchive();
            archive.setCreatedAt(now);
        }
        archive.setUserId(user.getId());
        archive.setEmployeeNo(username);
        archive.setName(profile.getName());
        archive.setPhone(profile.getPhone());
        archive.setEmail(profile.getEmail());
        archive.setSpecializations(writeSpecializations(profile.getSpecializations()));
        archive.setSkillLevel(profile.getSkillLevel());
        archive.setCertification(profile.getCertification());
        archive.setUpdatedAt(now);
        if (archive.getId() == null) {
            maintenancePersonnelArchiveMapper.insert(archive);
        } else {
            maintenancePersonnelArchiveMapper.updateById(archive);
        }
    }

    private MaintenancePersonnel findSchedule(Integer userId, String username) {
        QueryWrapper<MaintenancePersonnel> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (StringUtils.hasText(username)) {
            if (userId != null) wrapper.or();
            wrapper.eq("employee_no", username);
        }
        return maintenancePersonnelMapper.selectOne(wrapper);
    }

    private MaintenancePersonnelArchive findArchive(Integer userId, String username) {
        QueryWrapper<MaintenancePersonnelArchive> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (StringUtils.hasText(username)) {
            if (userId != null) wrapper.or();
            wrapper.eq("employee_no", username);
        }
        return maintenancePersonnelArchiveMapper.selectOne(wrapper);
    }

    private void removeMaintenanceProfile(MaintenancePersonnel schedule, MaintenancePersonnelArchive archive,
                                          String oldRole) {
        if (schedule == null && archive == null && !MAINTENANCE_ROLE.equals(oldRole)) {
            return;
        }
        if (schedule != null && schedule.getCurrentWorkload() != null && schedule.getCurrentWorkload() > 0) {
            throw new IllegalStateException("该维修工程师仍有处理中工单，请先完成或转派工单后再修改身份");
        }
        if (archive != null) {
            maintenancePersonnelArchiveMapper.deleteById(archive.getId());
        }
        if (schedule != null) {
            workOrderTransferRequestMapper.delete(new QueryWrapper<WorkOrderTransferRequest>()
                    .eq("requester_personnel_id", schedule.getId())
                    .or().eq("new_personnel_id", schedule.getId()));
            maintenancePersonnelMapper.deleteById(schedule.getId());
        }
    }

    private String writeSpecializations(List<String> specializations) {
        try {
            return objectMapper.writeValueAsString(specializations == null ? List.of() : specializations);
        } catch (Exception e) {
            throw new IllegalArgumentException("维修人员专长格式不合法", e);
        }
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

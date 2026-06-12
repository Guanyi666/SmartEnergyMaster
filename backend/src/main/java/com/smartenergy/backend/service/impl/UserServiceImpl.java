package com.smartenergy.backend.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.config.JwtConfig;
import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.service.AuditLogService;
import com.smartenergy.backend.service.LoginSessionService;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.LoginVO;
import com.smartenergy.backend.vo.PageVO;
import com.smartenergy.backend.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
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
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("不能删除内置管理员账号");
        }
        sysUserMapper.deleteById(id);
        auditLogService.record("DELETE", "USER", "SYS_USER", String.valueOf(id),
                java.util.Map.of("username", user.getUsername()));
    }

    private SysUser requireUser(Integer id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return user;
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
}

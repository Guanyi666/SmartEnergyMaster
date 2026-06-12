package com.smartenergy.backend.service;

import com.smartenergy.backend.config.JwtConfig;
import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.mapper.UserWithPersonnelMapper;
import com.smartenergy.backend.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private UserWithPersonnelMapper userWithPersonnelMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @Mock private JwtConfig jwtConfig;
    @Mock private AuditLogService auditLogService;
    @Mock private LoginSessionService loginSessionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void builtInAdminCannotBeDemoted() {
        authenticate("admin", "ADMIN");
        when(sysUserMapper.selectById(1)).thenReturn(user(1, "admin", "ADMIN"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().updateUser(1, request("admin", "MAINTENANCE_ENGINEER"))
        );

        assertEquals("内置管理员 admin 永远只能是系统管理员", exception.getMessage());
    }

    @Test
    void adminCanChangeAnotherAdminRole() {
        authenticate("admin", "ADMIN");
        SysUser otherAdmin = user(2, "other-admin", "ADMIN");
        when(sysUserMapper.selectById(2)).thenReturn(otherAdmin);
        when(sysUserMapper.exists(any())).thenReturn(false);

        service().updateUser(2, request("other-admin", "OPERATOR"));

        assertEquals("OPERATOR", otherAdmin.getRole());
    }

    @Test
    void nonAdminCannotChangeAdminRole() {
        authenticate("hr", "HR_MANAGER");
        when(sysUserMapper.selectById(2)).thenReturn(user(2, "other-admin", "ADMIN"));

        assertThrows(
                AccessDeniedException.class,
                () -> service().updateUser(2, request("other-admin", "OPERATOR"))
        );
    }

    private UserServiceImpl service() {
        return new UserServiceImpl(
                sysUserMapper,
                userWithPersonnelMapper,
                passwordEncoder,
                authenticationManager,
                jwtConfig,
                auditLogService,
                loginSessionService
        );
    }

    private void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }

    private SysUser user(int id, String username, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }

    private UserUpsertRequest request(String username, String role) {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setUsername(username);
        request.setRole(role);
        request.setNickname(username);
        return request;
    }
}

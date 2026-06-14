package com.smartenergy.backend.service;

import com.smartenergy.backend.config.JwtConfig;
import com.smartenergy.backend.dto.AccountSettingsRequest;
import com.smartenergy.backend.dto.MaintenanceProfileRequest;
import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.entity.MaintenancePersonnelArchive;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.mapper.UserWithPersonnelMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelArchiveMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelMapper;
import com.smartenergy.backend.mapper.WorkOrderTransferRequestMapper;
import com.smartenergy.backend.service.impl.UserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private UserWithPersonnelMapper userWithPersonnelMapper;
    @Mock private MaintenancePersonnelMapper maintenancePersonnelMapper;
    @Mock private MaintenancePersonnelArchiveMapper maintenancePersonnelArchiveMapper;
    @Mock private WorkOrderTransferRequestMapper workOrderTransferRequestMapper;
    @Mock private ObjectMapper objectMapper;
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
        authenticate("2026010001", "ADMIN");
        when(sysUserMapper.selectById(1)).thenReturn(user(1, "2026010001", "ADMIN"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().updateUser(1, request("2026010001", "MAINTENANCE_ENGINEER"))
        );

        assertEquals("内置管理员 2026010001 永远只能是系统管理员", exception.getMessage());
    }

    @Test
    void adminCanChangeAnotherAdminRole() {
        authenticate("2026010001", "ADMIN");
        SysUser otherAdmin = user(2, "2025010002", "ADMIN");
        when(sysUserMapper.selectById(2)).thenReturn(otherAdmin);
        when(sysUserMapper.exists(any())).thenReturn(false);

        service().updateUser(2, request("2025050002", "OPERATOR"));

        assertEquals("OPERATOR", otherAdmin.getRole());
        assertEquals("2025050002", otherAdmin.getUsername());
    }

    @Test
    void nonAdminCannotChangeAdminRole() {
        authenticate("2026040001", "HR_MANAGER");
        when(sysUserMapper.selectById(2)).thenReturn(user(2, "2025010002", "ADMIN"));

        assertThrows(
                AccessDeniedException.class,
                () -> service().updateUser(2, request("2025050002", "OPERATOR"))
        );
    }

    @Test
    void rejectsAccountWhoseMarkerDoesNotMatchRole() {
        authenticate("2026010001", "ADMIN");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().createUser(requestWithPassword("2026010002", "MANAGER"))
        );

        assertEquals("账号身份标识与角色不匹配，MANAGER 应使用 02", exception.getMessage());
    }

    @Test
    void nonAdminCannotChangeExistingAccount() {
        authenticate("2026040001", "HR_MANAGER");
        when(sysUserMapper.selectById(2)).thenReturn(user(2, "2026050001", "OPERATOR"));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> service().updateUser(2, request("2026050002", "OPERATOR"))
        );

        assertEquals("只有系统管理员可以修改用户账号", exception.getMessage());
    }

    @Test
    void becomingMaintenanceEngineerCreatesProfileAndSchedule() throws Exception {
        authenticate("2026010001", "ADMIN");
        SysUser operator = user(2, "2026050001", "OPERATOR");
        when(sysUserMapper.selectById(2)).thenReturn(operator);
        when(sysUserMapper.exists(any())).thenReturn(false);
        when(objectMapper.writeValueAsString(any())).thenReturn("[\"电气\"]");

        UserUpsertRequest request = request("2026030002", "MAINTENANCE_ENGINEER");
        request.setMaintenanceProfile(maintenanceProfile());
        service().updateUser(2, request);

        verify(maintenancePersonnelMapper).insert(any(MaintenancePersonnel.class));
        verify(maintenancePersonnelArchiveMapper).insert(any(MaintenancePersonnelArchive.class));
        assertEquals("MAINTENANCE_ENGINEER", operator.getRole());
    }

    @Test
    void leavingMaintenanceEngineerDeletesProfileAndSchedule() {
        authenticate("2026010001", "ADMIN");
        SysUser engineer = user(2, "2026030002", "MAINTENANCE_ENGINEER");
        MaintenancePersonnel schedule = new MaintenancePersonnel();
        schedule.setId(10L);
        schedule.setCurrentWorkload(0);
        MaintenancePersonnelArchive archive = new MaintenancePersonnelArchive();
        archive.setId(11L);
        when(sysUserMapper.selectById(2)).thenReturn(engineer);
        when(sysUserMapper.exists(any())).thenReturn(false);
        when(maintenancePersonnelMapper.selectOne(any())).thenReturn(schedule);
        when(maintenancePersonnelArchiveMapper.selectOne(any())).thenReturn(archive);

        service().updateUser(2, request("2026050002", "OPERATOR"));

        verify(maintenancePersonnelArchiveMapper).deleteById(11L);
        verify(maintenancePersonnelMapper).deleteById(10L);
        assertEquals("OPERATOR", engineer.getRole());
    }

    @Test
    void cannotRemoveMaintenanceRoleWhileWorkOrdersAreActive() {
        authenticate("2026010001", "ADMIN");
        SysUser engineer = user(2, "2026030002", "MAINTENANCE_ENGINEER");
        MaintenancePersonnel schedule = new MaintenancePersonnel();
        schedule.setId(10L);
        schedule.setCurrentWorkload(1);
        when(sysUserMapper.selectById(2)).thenReturn(engineer);
        when(sysUserMapper.exists(any())).thenReturn(false);
        when(maintenancePersonnelMapper.selectOne(any())).thenReturn(schedule);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service().updateUser(2, request("2026050002", "OPERATOR"))
        );

        assertEquals("该维修工程师仍有处理中工单，请先完成或转派工单后再修改身份", exception.getMessage());
    }

    @Test
    void currentUserCanUpdateContactInfoWithoutChangingPassword() {
        SysUser currentUser = user(2, "2026050001", "OPERATOR");
        when(sysUserMapper.selectOne(any())).thenReturn(currentUser);
        AccountSettingsRequest request = new AccountSettingsRequest();
        request.setPhone("13800138000");
        request.setEmail("operator@example.com");

        var result = service().updateAccountSettings(currentUser.getUsername(), request);

        assertEquals("13800138000", currentUser.getPhone());
        assertEquals("operator@example.com", currentUser.getEmail());
        assertFalse(result.isPasswordChanged());
        verify(sysUserMapper).updateById(currentUser);
    }

    @Test
    void changingPasswordRequiresCorrectCurrentPassword() {
        SysUser currentUser = user(2, "2026050001", "OPERATOR");
        currentUser.setPassword("encoded-old-password");
        when(sysUserMapper.selectOne(any())).thenReturn(currentUser);
        when(passwordEncoder.matches("wrong-password", currentUser.getPassword())).thenReturn(false);
        AccountSettingsRequest request = new AccountSettingsRequest();
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new-password");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().updateAccountSettings(currentUser.getUsername(), request)
        );

        assertEquals("当前密码不正确", exception.getMessage());
    }

    @Test
    void currentUserCanChangePassword() {
        SysUser currentUser = user(2, "2026050001", "OPERATOR");
        currentUser.setPassword("encoded-old-password");
        when(sysUserMapper.selectOne(any())).thenReturn(currentUser);
        when(passwordEncoder.matches("old-password", "encoded-old-password")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "encoded-old-password")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");
        AccountSettingsRequest request = new AccountSettingsRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password");

        var result = service().updateAccountSettings(currentUser.getUsername(), request);

        assertEquals("encoded-new-password", currentUser.getPassword());
        assertTrue(result.isPasswordChanged());
        verify(sysUserMapper).updateById(currentUser);
    }

    private UserServiceImpl service() {
        return new UserServiceImpl(
                sysUserMapper,
                userWithPersonnelMapper,
                maintenancePersonnelMapper,
                maintenancePersonnelArchiveMapper,
                workOrderTransferRequestMapper,
                objectMapper,
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
        return user;
    }

    private UserUpsertRequest request(String username, String role) {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setUsername(username);
        request.setRole(role);
        request.setNickname(username);
        return request;
    }

    private UserUpsertRequest requestWithPassword(String username, String role) {
        UserUpsertRequest request = request(username, role);
        request.setPassword("123456");
        return request;
    }

    private MaintenanceProfileRequest maintenanceProfile() {
        MaintenanceProfileRequest profile = new MaintenanceProfileRequest();
        profile.setName("张工");
        profile.setSkillLevel("SENIOR");
        profile.setSpecializations(List.of("电气"));
        profile.setMaxWorkload(5);
        return profile;
    }
}

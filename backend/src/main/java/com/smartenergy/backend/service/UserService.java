package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.AccountSettingsRequest;
import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.vo.AccountSettingsVO;
import com.smartenergy.backend.vo.PageVO;
import com.smartenergy.backend.vo.LoginVO;
import com.smartenergy.backend.vo.UserVO;
import com.smartenergy.backend.vo.UserWithPersonnelVO;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/18
 */
public interface UserService {
    /**
     * 用户登录
     *
     * @return
     */
    LoginVO login(LoginRequest loginRequest);

    void logout(String username, String token);

    AccountSettingsVO getAccountSettings(String username);

    AccountSettingsVO updateAccountSettings(String username, AccountSettingsRequest request);

    PageVO<UserVO> listUsers(int page, int size, String keyword, String role, String department, String status);

    UserVO createUser(UserUpsertRequest request);

    UserVO updateUser(Integer id, UserUpsertRequest request);

    UserVO updateStatus(Integer id, String status);

    void deleteUser(Integer id);

    /**
     * v6: 人员管理合并列表（sys_user LEFT JOIN maintenance_personnel + workorder_maintenance_personnel）
     */
    PageVO<UserWithPersonnelVO> listUsersWithPersonnel(int page, int size, String keyword,
                                                       String role, String department, String status,
                                                       Boolean isMaintenance);

    /**
     * ★ 批次 2 (C3 修复): 给 MaintenancePersonnelService.create() 调用,
     * 当通过"人员管理"添加维修人员而未传 userId 时,自动建立对应的 sys_user 账号。
     *
     * @param username 必须符合 AccountUsernameRules 的维修工程师格式 (年份+03+四位序号)
     * @param nickname 显示名(对应 personnel.name)
     * @param phone    手机
     * @param email    邮箱
     * @return 新建账号 ID
     * @throws IllegalArgumentException 用户名格式不符或已存在
     */
    Integer createMaintenanceAccount(String username, String nickname, String phone, String email);
}

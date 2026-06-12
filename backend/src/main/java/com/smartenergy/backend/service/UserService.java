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
}

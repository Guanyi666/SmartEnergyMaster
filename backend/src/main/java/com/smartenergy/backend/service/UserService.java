package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.RegisterRequest;
import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.vo.PageVO;
import com.smartenergy.backend.vo.LoginVO;
import com.smartenergy.backend.vo.UserVO;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/18
 */
public interface UserService {
    /**
     * 用户注册
     */
    void register(RegisterRequest registerRequest);

    /**
     * 用户登录
     *
     * @return
     */
    LoginVO login(LoginRequest loginRequest);

    void logout(String username, String token);

    PageVO<UserVO> listUsers(int page, int size, String keyword, String role, String department, String status);

    UserVO createUser(UserUpsertRequest request);

    UserVO updateUser(Integer id, UserUpsertRequest request);

    UserVO updateStatus(Integer id, String status);

    void deleteUser(Integer id);
}

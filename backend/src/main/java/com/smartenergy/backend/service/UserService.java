package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.RegisterRequest;
import com.smartenergy.backend.vo.LoginVO;

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
}

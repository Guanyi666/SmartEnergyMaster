package com.smartenergy.backend.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.RegisterRequest;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final byte[] JWT_KEY = "SmartEnergyMasterSecretKey".getBytes();

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Override
    public void register(RegisterRequest request) {
        boolean exists = sysUserMapper.exists(new QueryWrapper<SysUser>().eq("username", request.getUsername()));
        if (exists) {
            throw new IllegalArgumentException("该用户名已被注册");
        }

        SysUser newUser = new SysUser();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRole("OPERATOR");
        sysUserMapper.insert(newUser);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String role = authentication.getAuthorities().iterator().next().getAuthority();
        String token = JWT.create()
                .setPayload("username", request.getUsername())
                .setPayload("role", role)
                .setPayload("expire_time", System.currentTimeMillis() + 1000L * 60 * 60 * 24)
                .setKey(JWT_KEY)
                .sign();

        return LoginVO.builder()
                .token(token)
                .username(request.getUsername())
                .role(role)
                .build();
    }
}

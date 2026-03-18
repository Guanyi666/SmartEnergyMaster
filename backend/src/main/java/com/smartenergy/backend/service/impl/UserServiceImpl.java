package com.smartenergy.backend.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.RegisterRequest;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.LoginVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Data
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    // JWT 加密秘钥（实际企业项目中应配置在 application.yml 中）
    private static final byte[] JWT_KEY = "SmartEnergyMasterSecretKey".getBytes();

    @Override
    public void register(RegisterRequest request) {
        // 1. 校验用户名是否重复
        boolean exists = sysUserMapper.exists(
                new QueryWrapper<SysUser>().eq("username", request.getUsername())
        );
        if (exists) {
            throw new RuntimeException("该用户名已被注册");
        }

        // 2. 构建新用户实体
        SysUser newUser = new SysUser();
        newUser.setUsername(request.getUsername());

        // 🚨 核心规范：数据库绝对不能存明文密码，使用 BCrypt 加密
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));

        // 赋予默认角色 (你可以根据需要修改)
        newUser.setRole("OPERATOR");

        // 3. 插入数据库
        sysUserMapper.insert(newUser);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 1. 委托 AuthenticationManager 进行标准的 Spring Security 认证
        // 它底层会自动调用我们上一步写的 UserDetailsServiceImpl 去数据库查密码并用 BCrypt 比对
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 2. 提取认证成功后的角色信息
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        // 3. 签发 JWT Token (使用 Hutool)
        String token = JWT.create()
                .setPayload("username", request.getUsername())
                .setPayload("role", role)
                // 设置过期时间，比如 24 小时后过期
                .setPayload("expire_time", System.currentTimeMillis() + 1000 * 60 * 60 * 24)
                .setKey(JWT_KEY)
                .sign();

        // 4. 构建 VO 返回给 Controller
        return LoginVO.builder()
                .token(token)
                .username(request.getUsername())
                .role(role)
                .build();
    }
}
package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        return User.withUsername(sysUser.getUsername())
                .password(sysUser.getPassword())
                .authorities(sysUser.getRole() != null ? sysUser.getRole() : "OPERATOR")
                .disabled("DISABLED".equals(sysUser.getStatus()))
                .build();
    }
}

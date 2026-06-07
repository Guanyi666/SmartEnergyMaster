package com.smartenergy.backend.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DeviceMapper deviceMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedDefaultUser();
        seedDefaultDevices();
    }

    private void seedDefaultUser() {
        boolean exists = sysUserMapper.exists(new QueryWrapper<SysUser>().eq("username", "admin"));
        if (exists) {
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("123456"));
        admin.setRole("ADMIN");
        sysUserMapper.insert(admin);
    }

    private void seedDefaultDevices() {
        List<Device> defaults = List.of(
                buildDevice("EAF-01", "1号电弧炉", "ARC_FURNACE", "RUNNING", "炼钢一车间", "张工", "核心高耗能设备"),
                buildDevice("PUMP-01", "循环水泵", "PUMP", "STOPPED", "公辅站", "李工", "冷却系统关键泵组"),
                buildDevice("COMP-01", "空压机A", "COMPRESSOR", "STOPPED", "动力站", "王工", "压缩空气主设备")
        );

        for (Device device : defaults) {
            boolean exists = deviceMapper.exists(new QueryWrapper<Device>().eq("device_code", device.getDeviceCode()));
            if (!exists) {
                deviceMapper.insert(device);
            }
        }
    }

    private Device buildDevice(String code, String name, String type, String status, String location, String maintainer, String description) {
        Device device = new Device();
        device.setDeviceCode(code);
        device.setDeviceName(name);
        device.setDeviceType(type);
        device.setStatus(status);
        device.setLocation(location);
        device.setMaintainer(maintainer);
        device.setDescription(description);
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        return device;
    }
}

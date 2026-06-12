package com.smartenergy.backend.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.smartenergy.backend.utils.AccountUsernameRules.BUILT_IN_ADMIN_USERNAME;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DeviceMapper deviceMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.password:admin123}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
        seedDefaultUser();
        seedDefaultDevices();
    }

    private void seedDefaultUser() {
        SysUser existingAdmin = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", BUILT_IN_ADMIN_USERNAME));
        boolean migratedLegacyAdmin = false;
        if (existingAdmin == null) {
            existingAdmin = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", "admin"));
            if (existingAdmin != null) {
                existingAdmin.setUsername(BUILT_IN_ADMIN_USERNAME);
                migratedLegacyAdmin = true;
            }
        }
        if (existingAdmin != null) {
            boolean needsRepair = migratedLegacyAdmin
                    || !"ADMIN".equals(existingAdmin.getRole())
                    || !"ACTIVE".equals(existingAdmin.getStatus());
            if (needsRepair) {
                existingAdmin.setUsername(BUILT_IN_ADMIN_USERNAME);
                existingAdmin.setRole("ADMIN");
                existingAdmin.setStatus("ACTIVE");
                existingAdmin.setUpdatedAt(LocalDateTime.now());
                sysUserMapper.updateById(existingAdmin);
            }
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(BUILT_IN_ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
        admin.setRole("ADMIN");
        admin.setStatus("ACTIVE");
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(admin);
    }

    private void seedDefaultDevices() {
        List<Device> defaults = List.of(
                buildDevice("EAF-01", "1号电弧炉", "ARC_FURNACE", "RUNNING", "炼钢一车间", "张工", "核心高耗能设备—废钢熔化与初步合金化"),
                buildDevice("PUMP-01", "循环水泵", "PUMP", "STOPPED", "公辅站", "李工", "冷却系统关键泵组—向电弧炉/连铸提供冷却水"),
                buildDevice("COMP-01", "空压机A", "COMPRESSOR", "STOPPED", "动力站", "王工", "压缩空气主设备—气动阀门与仪表气源"),
                buildDevice("LF-01", "钢包精炼炉", "LADLE_FURNACE", "STOPPED", "炼钢一车间", "赵工", "钢水二次精炼—合金化/脱硫/调温"),
                buildDevice("CC-01", "1号连铸机", "CONTINUOUS_CASTER", "STOPPED", "连铸跨", "钱工", "钢水连续浇铸成坯—弧形连铸机"),
                buildDevice("DC-01", "主除尘系统", "DUST_COLLECTOR", "STOPPED", "环保站", "孙工", "电弧炉烟气捕集与布袋除尘—环保合规")
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

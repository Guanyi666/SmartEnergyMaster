package com.smartenergy.backend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.mapper.DeviceMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.smartenergy.backend.mapper")
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    // =============测试用！！！！=====================
    // CommandLineRunner 会在 Spring Boot 启动完成后自动执行
    @Bean
    public CommandLineRunner testDatabaseQuery(DeviceMapper deviceMapper) {
        return args -> {
            System.out.println("====== 开始测试数据库连接与查询 ======");

            // 构造查询条件：查出 device_code 是 'BF-01' 的设备
            QueryWrapper<Device> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("device_code", "EAF-01");

            // 执行查询
            Device bf01 = deviceMapper.selectOne(queryWrapper);

            if (bf01 != null) {
                System.out.println("✅ 成功查到设备！");
                System.out.println("设备名称: " + bf01.getDeviceName());
                System.out.println("设备类型: " + bf01.getDeviceType());
            } else {
                System.out.println("❌ 未找到设备，请检查 init.sql 是否正确执行。");
            }
            System.out.println("===================================");
        };
    }
}

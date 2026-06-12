package com.smartenergy.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 🆕 workorder 模块已合并到 com.smartenergy.backend 包下。
 * scanBasePackages = "com.smartenergy" 保留以兼容任何外部扩展包。
 */
@SpringBootApplication(scanBasePackages = "com.smartenergy")
@MapperScan("com.smartenergy.backend.mapper")
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}

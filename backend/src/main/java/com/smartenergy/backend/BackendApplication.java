package com.smartenergy.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 🆕 合并 workorder-backend: scanBasePackages = "com.smartenergy"
 * 之前 @SpringBootApplication 默认只扫 com.smartenergy.backend.*，
 * 合并后 com.smartenergy.workorder.* 下的 @Controller / @Service / @Configuration 都扫不到。
 * 显式指定 scanBasePackages 让两个 namespace 同时被加载。
 */
@SpringBootApplication(scanBasePackages = "com.smartenergy")
@MapperScan({"com.smartenergy.backend.mapper", "com.smartenergy.workorder.mapper"})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}

package com.smartenergy.workorder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.smartenergy.workorder.mapper")
@EnableScheduling
public class WorkorderApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkorderApplication.class, args);
    }
}

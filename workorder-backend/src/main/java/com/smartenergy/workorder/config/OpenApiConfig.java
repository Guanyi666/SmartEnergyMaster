package com.smartenergy.workorder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workorderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("维修工单与人员调度 API")
                        .description("SmartEnergyMaster - 维修工单与人员调度独立服务（端口 8081）")
                        .version("v0.1.0")
                        .contact(new Contact()
                                .name("第25组-智驭能效")
                                .email("group25@smartenergy.local"))
                        .license(new License()
                                .name("内部项目")));
    }
}

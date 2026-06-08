package com.smartenergy.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智驭能效（SmartEnergyMaster）API 文档")
                        .version("1.0.0")
                        .description("工业能源管理平台后端接口，提供设备监控、传感器数据采集、故障检测、维修工单管理和仪表盘聚合统计等功能。")
                        .contact(new Contact()
                                .name("第25组-智驭能效")
                                .email("admin@smartenergy.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .name("Bearer")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Token，格式：Bearer {token}。登录成功后获取 Token 填入此处。")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("smart-energy")
                .displayName("智驭能效 API")
                .pathsToMatch("/api/**")
                .build();
    }
}

package com.jiayuan.boot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * OpenAPI接口文档配置
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OpenApiConfig {

    private final Environment environment;

    /**
     * 接口文档信息
     */
    @Bean
    public OpenAPI openApi() {

        String appVersion = environment.getProperty("project.version", "1.0.0");
        return new OpenAPI()
                .info(new Info()
                        .title("系统 API 文档")
                        .description("本文档涵盖系统的所有 API，提供详细的接口说明和使用指南。")
                        .version(appVersion)
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0")));
    }

}

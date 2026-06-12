package com.housingfund.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智慧公积金综合管理系统 API")
                        .version("1.0.0")
                        .description("提供单位缴存申报、个人提取、贷款审批、还款管理、资金报表等全套公积金业务接口")
                        .contact(new Contact()
                                .name("住房公积金管理中心")
                                .email("support@housingfund.com")));
    }
}

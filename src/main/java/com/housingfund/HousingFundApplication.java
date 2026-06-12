package com.housingfund;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.housingfund.mapper")
@EnableScheduling
public class HousingFundApplication {

    public static void main(String[] args) {
        SpringApplication.run(HousingFundApplication.class, args);
    }
}

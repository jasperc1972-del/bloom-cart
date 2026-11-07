package com.dora.flowerstore;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 朵拉花店 (Dora Floral) 啟動類別
 */
@SpringBootApplication
@MapperScan("com.dora.flowerstore.mapper")
public class FlowerStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowerStoreApplication.class, args);
    }
}

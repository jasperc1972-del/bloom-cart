package com.dora.flowerstore;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 朵拉花店 (Dora Floral) 啟動類別
 */
@SpringBootApplication
@EnableAsync  // 通知信非同步寄送，不拖住下單回應
@MapperScan("com.dora.flowerstore.mapper")
public class FlowerStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowerStoreApplication.class, args);
    }
}

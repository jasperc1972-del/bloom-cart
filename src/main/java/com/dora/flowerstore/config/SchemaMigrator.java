package com.dora.flowerstore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 舊資料庫自動補欄位：schema.sql 用 CREATE TABLE IF NOT EXISTS，已存在的表不會加新欄位，
 * MySQL 8 又不支援 ADD COLUMN IF NOT EXISTS，所以啟動時查 information_schema，缺的才 ALTER
 */
@Component
@Order(0)
public class SchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    /** orders 表要補的欄位：欄位名 → 定義（依序加在指定欄位後面） */
    private static final Map<String, String> ORDER_COLUMNS = new LinkedHashMap<>();

    static {
        ORDER_COLUMNS.put("batch_no", "VARCHAR(64) NULL AFTER order_no");
        ORDER_COLUMNS.put("payer_name", "VARCHAR(50) NULL AFTER shipping_address");
        ORDER_COLUMNS.put("payer_phone", "VARCHAR(20) NULL AFTER payer_name");
        ORDER_COLUMNS.put("payer_email", "VARCHAR(100) NULL AFTER payer_phone");
    }

    private final JdbcTemplate jdbc;

    public SchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        ORDER_COLUMNS.forEach((column, definition) -> {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = ?""",
                    Integer.class, column);
            if (count != null && count == 0) {
                // 欄位名與定義都是上面寫死的常數，不是使用者輸入，沒有注入風險
                jdbc.execute("ALTER TABLE orders ADD COLUMN " + column + " " + definition);
                log.info("已為 orders 表補上欄位 {}", column);
            }
        });
    }
}

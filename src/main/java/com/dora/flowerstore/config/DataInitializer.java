package com.dora.flowerstore.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dora.flowerstore.entity.User;
import com.dora.flowerstore.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 啟動時若沒有管理員帳號就自動建立一個（密碼在執行時才雜湊，不把雜湊值寫死在 SQL）
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataInitializer(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.username:}") String adminUsername,
                           @Value("${app.admin.password:}") String adminPassword) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        // 程式碼裡不放任何預設帳密；沒在 config/application.yml 設定就不建立管理員
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            log.warn("未設定 app.admin.username / app.admin.password，略過建立管理員帳號");
            return;
        }
        if (adminPassword.length() < 8) {
            log.warn("管理員密碼少於 8 字，略過建立管理員帳號");
            return;
        }
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, adminUsername));
        if (exists) {
            return;
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userMapper.insert(admin);
        log.info("已建立管理員帳號");
    }
}

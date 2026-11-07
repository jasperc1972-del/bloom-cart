package com.dora.flowerstore.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 會員（users）
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** BCrypt 雜湊值 */
    private String password;
    private String email;
    /** MEMBER / ADMIN */
    private String role;
    private LocalDateTime createdAt;
}

package com.dora.flowerstore.dto;

/**
 * 目前登入者資訊（不含密碼）
 */
public record UserView(Long id, String username, String email, String role) {
}

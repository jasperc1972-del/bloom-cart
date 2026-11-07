package com.dora.flowerstore.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "請輸入帳號") String username,
        @NotBlank(message = "請輸入密碼") String password
) {
}

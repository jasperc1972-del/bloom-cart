package com.dora.flowerstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "請輸入帳號")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,50}$", message = "帳號需為 4~50 個英數字或底線")
        String username,
        @NotBlank(message = "請輸入密碼")
        @Size(min = 8, max = 64, message = "密碼長度需為 8~64 字")
        String password,
        @Email(message = "電子郵件格式不正確")
        @Size(max = 100, message = "電子郵件過長")
        String email
) {
}

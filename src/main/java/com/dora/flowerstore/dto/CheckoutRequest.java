package com.dora.flowerstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 結帳請求：收件資訊 + 指定送達日期與時段
 */
public record CheckoutRequest(
        @NotBlank(message = "請輸入收件人姓名") @Size(max = 50, message = "收件人姓名過長") String recipientName,
        @NotBlank(message = "請輸入收件人電話")
        @Pattern(regexp = "^[0-9+\\-() ]{8,20}$", message = "電話格式不正確")
        String recipientPhone,
        @NotBlank(message = "請輸入送貨地址") @Size(max = 255, message = "地址過長") String shippingAddress,
        @NotNull(message = "請選擇指定送達日期") LocalDate deliveryDate,
        @NotBlank(message = "請選擇配送時段") String deliveryTimeSlot
) {
}
